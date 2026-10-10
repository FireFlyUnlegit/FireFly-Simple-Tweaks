package dev.firefly.simpletweaks.enchantments.handlers.rare

import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.compat.WorldSide
import dev.firefly.simpletweaks.compat.event.EventPriority
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.event.TickEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.projectile.PersistentProjectileEntity
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.world.ServerWorld
import java.lang.ref.WeakReference
import java.util.UUID
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment

/**
 * 1.21 port of `enchantments/handlers/rare/EnchantHuntersMarkHandler.kt`.
 *
 * Line-by-line mapping from 1.12.2:
 * | 1.12.2                                            | 1.21.1                                                                |
 * |---------------------------------------------------|-----------------------------------------------------------------------|
 * | `net.minecraftforge...LivingHurtEvent`            | `compat.event.LivingHurtEvent`                                        |
 * | `net.minecraftforge...TickEvent`                  | `compat.event.TickEvent`                                              |
 * | `EntityLivingBase`                                | `net.minecraft.entity.LivingEntity`                                   |
 * | `e.source.trueSource`                             | `e.source.attacker` (Yarn `DamageSource.getAttacker`, method_5529)     |
 * | `e.source.immediateSource`                        | `e.source.source` (Yarn `DamageSource.getSource`, method_5509)         |
 * | `EntityArrow`                                     | `net.minecraft.entity.projectile.PersistentProjectileEntity` (the 1.21 common base of `ArrowEntity`/`SpectralArrowEntity`) |
 * | `arrow.shootingEntity`                            | `arrow.owner` (Yarn `ProjectileEntity.getOwner`, method_24921)        |
 * | `target.uniqueID`                                 | `target.uuid` (`EntityLike.getUuid`, method_5667)                     |
 * | `entity.isDead` / `!entity.isEntityAlive`         | `entity.isRemoved` (method_5805) / `!entity.isAlive` (method_5805 family) |
 * | `entity.ticksExisted`                             | `entity.age` (public field `field_6005`)                              |
 * | `entity.posX/posY/posZ` / `entity.height`         | `entity.x/y/z` / `entity.height` (`getHeight`, method_17682)          |
 * | `world.isRemote`                                  | `WorldSide.isClient(world)` (field_9236 is a FIELD)                   |
 * | `WorldServer.spawnParticle(EnumParticleTypes.CRIT, ...)` | `ServerWorld.spawnParticles(ParticleTypes.CRIT, ...)` (`method_14199`) |
 * | `EnumParticleTypes.CRIT_MAGIC`                    | `ParticleTypes.ENCHANTED_HIT` (1.13 renamed the `crit_magic` particle to `enchanted_hit`) |
 * | `EnchantHuntersMark` (Enchantment object)         | `GeneratedEnchantments.HUNTERS_MARK` (RegistryKey)                       |
 *
 * No behavioural change: the whole state machine (10 s expiry, level refresh taking the max, the
 * one-shot consume on the next non-arrow hit) is carried over verbatim.
 */
@ModEnchantment(
    id = "hunters_mark",
    category = EnchantCategory.RARE,
    type = EnchantType.BOW,
    maxLevel = 3,
    weight = 6,
    anvilCost = 6,
    minCostBase = 30,
    minCostPerLevel = 10,
    supportedItems = "#minecraft:enchantable/bow",
    slots = [EnchantSlot.MAINHAND, EnchantSlot.OFFHAND],
    order = 20,
)
object EnchantHuntersMarkHandler : Listenable {
    private class MarkState(val ref: WeakReference<LivingEntity>) {
        var expireAtMs: Long = 0
        var lvl: Int = 0
    }

    private val marks = mutableMapOf<UUID, MarkState>()

    @SubscribeEvent(priority = EventPriority.LOW)
    fun onHurt(e: LivingHurtEvent) {
        val target = e.entityLiving ?: return
        val attacker = e.source.attacker as? PlayerEntity ?: return
        if (attacker === target) return

        val arrow = e.source.source as? PersistentProjectileEntity
        val isArrowHit = arrow != null && arrow.owner === attacker

        if (isArrowHit) {
            val lvl = getItemSpecificEnchantLevel(attacker.mainHandStack, GeneratedEnchantments.HUNTERS_MARK)
            if (lvl <= 0) return
            applyMark(target, lvl)
            spawnMarkFx(target)
            STLog.log("HuntersMark") {
                "shooter=${attacker.name.string}, target=${target.name.string}, lvl=$lvl, " +
                    "expireAtMs=${System.currentTimeMillis() + 10_000L}, outcome=marked"
            }
        } else {
            val state = marks[target.uuid] ?: return
            if (state.ref.get() !== target) {
                marks.remove(target.uuid)
                return
            }
            if (System.currentTimeMillis() > state.expireAtMs) {
                marks.remove(target.uuid)
                return
            }

            e.amount *= 1f + 0.2f * state.lvl
            marks.remove(target.uuid)

            spawnConsumeFx(target)
            STLog.log("HuntersMark") {
                "attacker=${attacker.name.string}, target=${target.name.string}, markLvl=${state.lvl}, " +
                    "damage=${e.amount}, outcome=mark-consumed"
            }
        }
    }

    private fun applyMark(target: LivingEntity, lvl: Int) {
        val now = System.currentTimeMillis()
        val id = target.uuid
        val existing = marks[id]
        if (existing != null && existing.ref.get() === target) {
            if (lvl > existing.lvl) existing.lvl = lvl
            existing.expireAtMs = now + 10_000L
        } else {
            marks[id] = MarkState(WeakReference(target)).also {
                it.expireAtMs = now + 10_000L
                it.lvl = lvl
            }
        }
    }


    private fun spawnMarkFx(target: LivingEntity) {
        val world = target.world as? ServerWorld ?: return
        world.spawnParticles(
            ParticleTypes.CRIT,
            target.x,
            target.y + target.height * 0.7,
            target.z,
            8, 0.4, 0.4, 0.4, 0.05,
        )
    }

    private fun spawnConsumeFx(target: LivingEntity) {
        val world = target.world as? ServerWorld ?: return
        world.spawnParticles(
            ParticleTypes.ENCHANTED_HIT,
            target.x,
            target.y + target.height * 0.5,
            target.z,
            15, 0.5, 0.5, 0.5, 0.1,
        )
    }

    @SubscribeEvent
    fun onWorldTick(e: TickEvent.WorldTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        val world = e.world
        if (WorldSide.isClient(world)) return
        if (marks.isEmpty()) return

        val now = System.currentTimeMillis()
        val iter = marks.entries.iterator()
        while (iter.hasNext()) {
            val (_, state) = iter.next()
            val entity = state.ref.get()
            if (entity == null || entity.isRemoved || !entity.isAlive) {
                iter.remove()
                continue
            }
            if (now > state.expireAtMs) {
                iter.remove()
                continue
            }
            if (entity.age % 20 == 0 && world is ServerWorld) {
                world.spawnParticles(
                    ParticleTypes.CRIT,
                    entity.x,
                    entity.y + entity.height * 0.8,
                    entity.z,
                    2, 0.2, 0.2, 0.2, 0.0,
                )
            }
        }
    }
}
