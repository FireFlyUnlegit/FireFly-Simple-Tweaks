package dev.firefly.simpletweaks.enchantments.handlers.mythic

import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.compat.WorldSide
import dev.firefly.simpletweaks.compat.event.AttackEntityEvent
import dev.firefly.simpletweaks.compat.event.PlayerEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.event.TickEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.ModEnchantmentKeys
import dev.firefly.simpletweaks.util.canEntitySee
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.runPlayerAttack
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.mob.Monster
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.world.ServerWorld
import java.util.UUID
import java.util.WeakHashMap

/**
 * 1.21 port of `enchantments/handlers/mythic/EnchantKillAuraHandler.kt`.
 *
 * Line-by-line mapping from 1.12.2:
 * | 1.12.2                                                        | 1.21.1                                                                 |
 * |---------------------------------------------------------------|------------------------------------------------------------------------|
 * | `net.minecraftforge...gameevent.TickEvent`                    | `compat.event.TickEvent`                                                |
 * | `net.minecraftforge...entity.player.AttackEntityEvent`        | `compat.event.AttackEntityEvent`                                        |
 * | `net.minecraftforge...gameevent.PlayerEvent.PlayerLoggedOutEvent` | `compat.event.PlayerEvent.PlayerLoggedOutEvent`                     |
 * | `AttackEntityEvent.entityPlayer`                              | `AttackEntityEvent.player`                                              |
 * | `EntityLivingBase`                                            | `net.minecraft.entity.LivingEntity`                                      |
 * | `IMob`                                                       | `net.minecraft.entity.mob.Monster` (the 1.21 hostile-mob marker interface) |
 * | `p.world.isRemote`                                            | `WorldSide.isClient(p.world)` (field_9236 is a FIELD)                    |
 * | `p.isDead`                                                    | `p.isRemoved` (`method_31481`)                                          |
 * | `p.heldItemMainhand`                                          | `p.mainHandStack` (`method_6047`)                                       |
 * | `p.uniqueID`                                                  | `p.uuid` (`EntityLike.getUuid`, method_5667)                            |
 * | `p.getEntityAttribute(ATTACK_DAMAGE).attributeValue`          | `p.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE)` (`method_45318`) |
 * | `p.heldItemMainhand.damageItem(1, p)`                         | `p.mainHandStack.damage(1, p, EquipmentSlot.MAINHAND)` (`method_7970`)  |
 * | `p.addExhaustion(0.5f)`                                       | unchanged (`method_7322`)                                               |
 * | `p.getDistanceSq(e)`                                          | `p.squaredDistanceTo(e)` (`method_5858`)                                |
 * | `p.entityBoundingBox.grow(range)`                             | `p.boundingBox.expand(range)` (`method_1014`)                           |
 * | `p.world.getEntitiesWithinAABB(EntityLivingBase::class.java, box)` | `p.world.getEntitiesByClass(LivingEntity::class.java, box) { true }` (`method_8390`) |
 * | `e.isOnSameTeam(p)`                                           | `e.isTeammate(p)` (`method_5722`)                                       |
 * | `e.isEntityInvulnerable(source)`                              | `e.isInvulnerableTo(source)` (`method_5679`)                            |
 * | `e.revengeTarget`                                             | `e.attacker` (`LivingEntity.getAttacker`, `method_6065`)                 |
 * | `e.isEntityAlive` / `e.isDead`                                | `e.isAlive` / `e.isRemoved`                                             |
 * | `p.world.canEntitySee(p, e)`                                  | same extension, ported to `util/WorldExtensions.kt` over `World.raycast` |
 * | `DamageSource.causePlayerDamage(p)`                           | `p.damageSources.playerAttack(p)` (`method_48802`)                      |
 * | `EnchantKillAura` (Enchantment object)                        | `ModEnchantmentKeys.KILL_AURA` (RegistryKey)                            |
 *
 * ⚠️ One forced mapping — the weapon's enchantment damage bonus.
 * 1.12.2 called `EnchantmentHelper.getModifierForCreature(weapon, target.creatureAttribute)`, which
 * summed Sharpness / Smite / Bane by comparing `EnumCreatureAttribute`. 1.13 deleted that enum and
 * 1.21 expresses the same thing as the `minecraft:damage` enchantment effect, conditioned on
 * `EntityType` tags. The vanilla equivalent is `EnchantmentHelper.getDamage(world, stack, target,
 * source, baseDamage)` — it is literally what `ServerPlayerEntity#getDamageAgainst` calls — and it
 * returns `baseDamage + bonus`, so passing `0f` yields the bonus alone, exactly like the old call.
 * **Verified equivalent, not a superset.** An earlier note here claimed `getModifierForCreature` saw
 * only vanilla Sharpness/Smite/Bane; that was wrong. It summed `Enchantment#calcDamageByCreature`
 * over *every* enchantment on the stack, and this mod's `ModEnchantments` overrides that method
 * (returning `(0.2 + rarity/20) * level` and ignoring the creature type) — so the mod's own damage
 * enchantments were already included in the 1.12.2 total. `getDamage` likewise evaluates every
 * `minecraft:damage` effect, and every enchantment JSON this mod generates carries exactly that same
 * linear formula with no `requirements`. Vanilla Sharpness/Smite/Bane remain conditional through
 * `EntityType` tag predicates in their JSONs, matching the old creature-attribute check. The numbers
 * therefore agree, and no fallback to a vanilla-only sum is needed.
 *
 * ⚠️ Also inherited from `util/PlayerUtils.kt`: `runPlayerAttack`'s `allowCrit` branch is dropped in
 * this port (no `CriticalHitEvent` seam), so the aura hit cannot crit. The 1.12.2 code took the
 * vanilla crit roll here; this handler passes no `allowCrit` argument, i.e. it accepted the default
 * `true`.
 */
object EnchantKillAuraHandler : Listenable {

    private val cooldown = WeakHashMap<UUID, Int>()
    private val priorityTargets = WeakHashMap<UUID, MutableList<PriorityEntry>>()

    private var auraAttacking = false

    private class PriorityEntry(val entity: LivingEntity) {
        var ticksOutOfRange: Int = 0
    }

    private fun maxCooldown(lvl: Int): Int = (40 - 8 * lvl).coerceAtLeast(2)

    private fun rangeFor(lvl: Int): Double = (1.5 + 0.5 * lvl).coerceAtMost(3.5)

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        val p = e.player
        if (WorldSide.isClient(p.world)) return
        if (p.isRemoved) return

        val lvl = getItemSpecificEnchantLevel(p.mainHandStack, ModEnchantmentKeys.KILL_AURA)

        cleanPriorityTargets(p, if (lvl > 0) rangeFor(lvl) else 1.5)

        if (lvl <= 0) {
            cooldown.remove(p.uuid)
            return
        }

        val cd = (cooldown[p.uuid] ?: 0) - 1
        if (cd > 0) {
            cooldown[p.uuid] = cd
            return
        }

        val target = findTarget(p, lvl) ?: return

        val damage = p.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE).toFloat()
        val weapon = p.mainHandStack

        // 1.12.2: `EnchantmentHelper.getModifierForCreature(weapon, target.creatureAttribute)`.
        // 1.21 needs a ServerWorld + DamageSource to resolve the `minecraft:damage` effects; the
        // base damage is 0f so the result is the enchantment bonus alone (see the class KDoc).
        val world = p.world as? ServerWorld ?: return
        val source = p.damageSources.playerAttack(p)
        val enchantBonus = EnchantmentHelper.getDamage(world, weapon, target, source, 0f)
        val multiplier = 0.4 + (lvl * 0.1)

        auraAttacking = true
        val result = try {
            target.runPlayerAttack(p, (damage + enchantBonus) * multiplier, forceHit = true)
        } finally {
            auraAttacking = false
        }

        if (result > 0f) {
            p.addExhaustion(0.5f)
            p.mainHandStack.damage(1, p, EquipmentSlot.MAINHAND)
        }

        cooldown[p.uuid] = maxCooldown(lvl)
        STLog.log("KillAura") {
            "player=${p.name.string}, target=${target.name.string}, lvl=$lvl, baseDamage=$damage, " +
                "enchantBonus=$enchantBonus, multiplier=$multiplier, dealt=$result, " +
                "cooldown=${maxCooldown(lvl)}, range=${rangeFor(lvl)}"
        }
    }

    @SubscribeEvent
    fun onPlayerAttack(e: AttackEntityEvent) {
        if (auraAttacking) return

        val p = e.player
        if (WorldSide.isClient(p.world)) return

        val lvl = getItemSpecificEnchantLevel(p.mainHandStack, ModEnchantmentKeys.KILL_AURA)
        if (lvl <= 0) return

        (e.target as? LivingEntity)?.let { addPriorityTarget(p, it) }

        cooldown[p.uuid] = maxCooldown(lvl)
        STLog.log("KillAura") {
            "player=${p.name.string}, target=${e.target.name.string}, lvl=$lvl, " +
                "cooldown=${maxCooldown(lvl)}, outcome=priority-target-armed"
        }
    }

    @SubscribeEvent
    fun onLogout(e: PlayerEvent.PlayerLoggedOutEvent) {
        cooldown.remove(e.player.uuid)
        priorityTargets.remove(e.player.uuid)
    }


    private fun addPriorityTarget(p: PlayerEntity, target: LivingEntity) {
        val list = priorityTargets.getOrPut(p.uuid) { mutableListOf() }
        list.removeAll { it.entity === target }
        list.add(0, PriorityEntry(target))
    }

    private fun cleanPriorityTargets(p: PlayerEntity, range: Double) {
        val list = priorityTargets[p.uuid] ?: return
        if (list.isEmpty()) {
            priorityTargets.remove(p.uuid)
            return
        }

        val maxDistSq = range * range
        val iter = list.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            val e = entry.entity

            val remove = when {
                e.isRemoved || !e.isAlive -> true
                e.world !== p.world -> true
                p.squaredDistanceTo(e) > maxDistSq -> {
                    entry.ticksOutOfRange++
                    entry.ticksOutOfRange > 100
                }
                else -> {
                    entry.ticksOutOfRange = 0
                    false
                }
            }

            if (remove) iter.remove()
        }

        if (list.isEmpty()) priorityTargets.remove(p.uuid)
    }

    private fun hasMutualAttackHistory(p: PlayerEntity, e: LivingEntity): Boolean {
        if (p.attacker === e) return true
        if (e.attacker === p) return true
        val list = priorityTargets[p.uuid]
        return list != null && list.any { it.entity === e }
    }

    private fun isValidTarget(
        p: PlayerEntity,
        e: LivingEntity,
        source: DamageSource,
        maxDistSq: Double,
    ): Boolean {
        if (e === p) return false
        if (!e.isAlive) return false
        if (e.world !== p.world) return false
        if (p.squaredDistanceTo(e) > maxDistSq) return false
        if (e.isTeammate(p)) return false
        if (e.isInvulnerableTo(source)) return false

        if (e !is Monster && !hasMutualAttackHistory(p, e)) return false

        if (!p.world.canEntitySee(p, e)) return false

        return true
    }

    private fun findTarget(p: PlayerEntity, lvl: Int): LivingEntity? {
        val range = rangeFor(lvl)
        val maxDistSq = range * range
        val source = p.damageSources.playerAttack(p)

        priorityTargets[p.uuid]?.let { list ->
            for (entry in list) {
                val e = entry.entity
                if (isValidTarget(p, e, source, maxDistSq)) return e
            }
        }

        val box = p.boundingBox.expand(range)
        return p.world.getEntitiesByClass(LivingEntity::class.java, box) { true }
            .filter { isValidTarget(p, it, source, maxDistSq) }
            .minByOrNull { p.squaredDistanceTo(it) }
    }
}
