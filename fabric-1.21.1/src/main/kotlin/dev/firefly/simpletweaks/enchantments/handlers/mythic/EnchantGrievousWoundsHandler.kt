package dev.firefly.simpletweaks.enchantments.handlers.mythic

import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.compat.WorldSide
import dev.firefly.simpletweaks.compat.event.EventPriority
import dev.firefly.simpletweaks.compat.event.LivingHealEvent
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.event.TickEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.ModEnchantmentKeys
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.world.ServerWorld
import java.lang.ref.WeakReference
import java.util.UUID

/**
 * 1.21 port of `enchantments/handlers/mythic/EnchantGrievousWoundsHandler.kt`.
 *
 * Line-by-line mapping from 1.12.2:
 * | 1.12.2                                                   | 1.21.1                                                              |
 * |----------------------------------------------------------|---------------------------------------------------------------------|
 * | `net.minecraftforge...LivingHurtEvent` / `LivingHealEvent` | `compat.event.LivingHurtEvent` / `compat.event.LivingHealEvent`   |
 * | `net.minecraftforge...TickEvent`                         | `compat.event.TickEvent`                                            |
 * | `net.minecraftforge...EventPriority`                     | `compat.event.EventPriority`                                        |
 * | `EntityLivingBase`                                       | `net.minecraft.entity.LivingEntity`                                 |
 * | `EntityPlayer`                                           | `net.minecraft.entity.player.PlayerEntity`                          |
 * | `e.source.trueSource`                                    | `e.source.attacker` (Yarn `DamageSource.getAttacker`, method_5529)   |
 * | `attacker.heldItemMainhand`                              | `attacker.mainHandStack` (`method_6047`)                            |
 * | `target.uniqueID`                                        | `target.uuid` (`EntityLike.getUuid`, method_5667)                   |
 * | `world.isRemote`                                         | `WorldSide.isClient(world)` (field_9236 is a FIELD)                 |
 * | `world is WorldServer`                                   | `world is ServerWorld`                                              |
 * | `EnumParticleTypes.SPELL_WITCH`                          | `ParticleTypes.WITCH` (1.13 dropped the `spell_` prefix)            |
 * | `WorldServer.spawnParticle(type, x, y, z, count, dx, dy, dz, speed)` | `ServerWorld.spawnParticles(effect, x, y, z, count, dx, dy, dz, speed)` (`method_14199`) |
 * | `entity.isDead \|\| !entity.isEntityAlive`               | `entity.isRemoved \|\| !entity.isAlive`                             |
 * | `entity.ticksExisted`                                    | `entity.age` (public field `field_6012`)                            |
 * | `entity.posX/posY/posZ` / `entity.height`                | `entity.x/y/z` / `entity.height` (`method_17682`)                   |
 * | `EnchantGrievousWounds` (Enchantment object)             | `ModEnchantmentKeys.GRIEVOUS_WOUNDS` (RegistryKey)                  |
 *
 * No behavioural change: the whole state machine (wall-clock expiry, level refresh taking the max,
 * the ratio-based heal cut, the `>= 1f` full heal lockout) is carried over verbatim.
 */
object EnchantGrievousWoundsHandler : Listenable {

    private class WoundState(val ref: WeakReference<LivingEntity>) {
        var expireAtMs: Long = 0
        var ratio: Float = 0f
    }

    private val wounds = mutableMapOf<UUID, WoundState>()


    @SubscribeEvent
    fun onHurt(e: LivingHurtEvent) {
        val target = e.entityLiving ?: return
        val attacker = e.source.attacker as? PlayerEntity ?: return
        if (attacker === target) return

        val lvl = getItemSpecificEnchantLevel(attacker.mainHandStack, ModEnchantmentKeys.GRIEVOUS_WOUNDS)
        if (lvl <= 0) return

        val ratio = (0.2f * lvl).coerceAtMost(1.0f)
        val duration = 1000L + 250L * lvl
        val now = System.currentTimeMillis()

        val id = target.uuid
        val state = wounds[id]
        val refresh = state != null && state.ref.get() === target
        if (state == null || state.ref.get() !== target) {
            wounds[id] = WoundState(WeakReference(target)).also {
                it.expireAtMs = now + duration
                it.ratio = ratio
            }
        } else {
            if (ratio > state.ratio) state.ratio = ratio
            state.expireAtMs = now + duration
        }
        STLog.log("GrievousWounds") {
            "attacker=${attacker.name.string}, target=${target.name.string}, lvl=$lvl, ratio=$ratio, " +
                "durationMs=$duration, refreshed=$refresh, outcome=wound-applied"
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onHeal(e: LivingHealEvent) {
        val target = e.entityLiving ?: return
        val state = wounds[target.uuid] ?: return
        if (state.ref.get() !== target) {
            wounds.remove(target.uuid)
            return
        }
        if (System.currentTimeMillis() > state.expireAtMs) {
            wounds.remove(target.uuid)
            return
        }

        val ratio = state.ratio
        if (ratio >= 1f) {
            e.amount = 0f
            e.isCanceled = true
        } else {
            e.amount = e.amount * (1f - ratio)
        }
    }

    @SubscribeEvent
    fun onWorldTick(e: TickEvent.WorldTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        val world = e.world
        if (WorldSide.isClient(world)) return
        if (wounds.isEmpty()) return

        val now = System.currentTimeMillis()
        val iter = wounds.entries.iterator()
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
            if (entity.age % 10 == 0 && world is ServerWorld) {
                world.spawnParticles(
                    ParticleTypes.WITCH,
                    entity.x,
                    entity.y + entity.height * 0.8,
                    entity.z,
                    3, 0.3, 0.3, 0.3, 0.0,
                )
            }
        }
    }
}
