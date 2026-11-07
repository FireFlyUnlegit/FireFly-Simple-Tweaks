package dev.firefly.simpletweaks.enchantments.handlers.mythic

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.WorldSide
import dev.firefly.simpletweaks.compat.event.AttackEntityEvent
import dev.firefly.simpletweaks.compat.event.PlayerEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.event.TickEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.util.attackCharge
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
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantItemWeights
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSupportedItemTags
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment

@ModEnchantment(
    id = "kill_aura",
    category = EnchantCategory.MYTHIC,
    type = EnchantType.SWORD,
    maxLevel = 6,
    weight = EnchantItemWeights.MYTHIC,
    anvilCost = 12,
    minCostBase = 40,
    minCostPerLevel = 15,
    supportedItems = EnchantSupportedItemTags.SWORD,
    slots = [EnchantSlot.MAINHAND],
    order = 44,
)
object EnchantKillAuraHandler : Listenable {

    private val cooldown = WeakHashMap<UUID, Int>()
    private val priorityTargets = WeakHashMap<UUID, MutableList<PriorityEntry>>()

    private var auraAttacking = false

    private class PriorityEntry(val entity: LivingEntity) {
        var ticksOutOfRange: Int = 0
    }

    private fun maxCooldown(p: PlayerEntity, lvl: Int): Int {
        val attackSpeed = p.getAttributeValue(EntityAttributes.GENERIC_ATTACK_SPEED)
            .coerceAtLeast(0.1)
        val baseTicks = 20.0 / attackSpeed
        val multiplier = (7 - lvl).coerceAtLeast(1)
        return kotlin.math.ceil(baseTicks * multiplier).toInt().coerceAtLeast(1)
    }

    private fun rangeFor(lvl: Int): Double = (1.5 + 0.5 * lvl).coerceAtMost(3.5)

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        val p = e.player
        if (WorldSide.isClient(p.world)) return
        if (p.isRemoved) return

        val lvl = getItemSpecificEnchantLevel(p.mainHandStack, GeneratedEnchantments.KILL_AURA)

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

        // The swing has to be ready.
        //
        // [maxCooldown] already derives from the weapon's attack speed (`20 / attackSpeed × (7 - level)`),
        // so at **level VI it equals exactly one swing period** — and that is where a tick counter and the
        // weapon's real cooldown can drift apart (lag, a manual swing in between, the counter being reset
        // by `onPlayerAttack`). The charge is the authoritative answer to "is the swing ready", so it is
        // consulted here; `simulateCooldown = true` on the hit below then *consumes* it, which is what makes
        // the aura and a manual swing share one resource instead of stacking two free hits.
        //
        // Below VI this gate never rejects: the tick cooldown is 2–6 swing periods, i.e. far longer than a
        // full recharge, so the behaviour there is unchanged.
        val charge = p.attackCharge
        if (charge < 1.0f) return

        val target = findTarget(p, lvl) ?: return

        val damage = p.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE).toFloat()
        val weapon = p.mainHandStack

        val world = p.world as? ServerWorld ?: return
        val source = p.damageSources.playerAttack(p)
        val enchantBonus = EnchantmentHelper.getDamage(world, weapon, target, source, 0f)
        val multiplier = 0.4 + (lvl * 0.1)

        auraAttacking = true
        val result = try {
            target.runPlayerAttack(
                p, (damage + enchantBonus) * multiplier,
                forceHit = true,
                // Spend the attack charge, exactly as the manual swing this replaces would have: the aura
                // *is* the player's swing at this level, so it must not be a free extra hit on top of one.
                simulateCooldown = true,
            )
        } finally {
            auraAttacking = false
        }

        if (result > 0f) {
            p.addExhaustion(0.5f)
            p.mainHandStack.damage(1, p, EquipmentSlot.MAINHAND)
        }

        cooldown[p.uuid] = maxCooldown(p, lvl)
        FSTLog.log("KillAura") {
            "player=${p.name.string}, target=${target.name.string}, lvl=$lvl, baseDamage=$damage, " +
                    "enchantBonus=$enchantBonus, multiplier=$multiplier, charge=$charge, dealt=$result, " +
                    "cooldown=${maxCooldown(p, lvl)}, range=${rangeFor(lvl)}"
        }
    }

    @SubscribeEvent
    fun onPlayerAttack(e: AttackEntityEvent) {
        if (auraAttacking) return

        val p = e.player
        if (WorldSide.isClient(p.world)) return

        val lvl = getItemSpecificEnchantLevel(p.mainHandStack, GeneratedEnchantments.KILL_AURA)
        if (lvl <= 0) return

        (e.target as? LivingEntity)?.let { addPriorityTarget(p, it) }

        cooldown[p.uuid] = maxCooldown(p, lvl)
        FSTLog.log("KillAura") {
            "player=${p.name.string}, target=${e.target.name.string}, lvl=$lvl, " +
                    "cooldown=${maxCooldown(p, lvl)}, outcome=priority-target-armed"
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