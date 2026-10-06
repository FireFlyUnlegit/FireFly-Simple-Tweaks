package dev.firefly.simpletweaks.enchantments.handlers.epic

import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.compat.WorldSide
import dev.firefly.simpletweaks.compat.event.EventPriority
import dev.firefly.simpletweaks.compat.event.LivingDamageEvent
import dev.firefly.simpletweaks.compat.event.LivingDeathEvent
import dev.firefly.simpletweaks.compat.event.PlayerEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.event.TickEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.compat.target
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.ModEnchantmentKeys
import dev.firefly.simpletweaks.util.getArmorEnchantLevel
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.world.ServerWorld
import java.util.UUID

/**
 * 1.21 port of `enchantments/handlers/epic/EnchantDelayedRecoveryHandler.kt`.
 *
 * Line-by-line mapping from 1.12.2:
 * | 1.12.2                                          | 1.21.1                                                              |
 * |-------------------------------------------------|---------------------------------------------------------------------|
 * | `net.minecraftforge...LivingDamageEvent`        | `compat.event.LivingDamageEvent`                                    |
 * | `net.minecraftforge...LivingDeathEvent`         | `compat.event.LivingDeathEvent`                                     |
 * | `net.minecraftforge.fml.common.gameevent.TickEvent` | `compat.event.TickEvent`                                        |
 * | `net.minecraftforge.event.entity.player.PlayerEvent` **and** `...gameevent.PlayerEvent as Event1` | **both** become `compat.event.PlayerEvent`; the 1.12.2 `as Event1` alias is dropped because the two Forge classes are merged into this one |
 * | `PlayerEvent.Clone.isWasDeath`                  | `compat.event.PlayerEvent.Clone.wasDeath`                           |
 * | `PlayerEvent.Clone.entityPlayer` (NEW player)   | `PlayerEvent.Clone.player`                                          |
 * | `player.uniqueID`                               | `player.uuid` (`EntityLike.getUuid`, method_5667)                   |
 * | `p.world.isRemote`                              | `WorldSide.isClient(p.world)` (field_9236 is a FIELD)                |
 * | `p.isEntityAlive`                               | `p.isAlive` (method_5805 family)                                     |
 * | `p.ticksExisted`                                | `p.age` (public field `field_6005`)                                  |
 * | `p.posX/posY/posZ`                              | `p.x/y/z`                                                            |
 * | `p.heal(f)`                                     | unchanged (`LivingEntity.heal`, method_6043)                          |
 * | `WorldServer` / `spawnParticle(...)`            | `ServerWorld` / `spawnParticles(ParticleTypes.HEART, ...)` (`method_14199`) |
 * | `EnumParticleTypes.HEART`                       | `ParticleTypes.HEART` (unchanged name)                               |
 * | `EnchantDelayedRecovery` (Enchantment)          | `ModEnchantmentKeys.DELAYED_RECOVERY` (RegistryKey)                  |
 *
 * The pending-heal list, the 30/40/50/60/70 % ratio ladder, `delay = max(8 - lvl, 3)` seconds, the
 * per-tick payout and the `amount <= 0.001f || ticksLeft <= 0` removal condition are all unchanged.
 */
object EnchantDelayedRecoveryHandler : Listenable {

    private class PendingHeal(
        var amount: Float,
        var ticksLeft: Int,
        val perTick: Float,
    )

    private val pending = mutableMapOf<UUID, MutableList<PendingHeal>>()

    @SubscribeEvent(priority = EventPriority.LOW)
    fun onDamage(e: LivingDamageEvent) {
        if (e.invalid) return
        val player = e.target as? PlayerEntity ?: return

        val lvl = player.getArmorEnchantLevel(ModEnchantmentKeys.DELAYED_RECOVERY)
        if (lvl <= 0) return

        val ratio = when (lvl) {
            1 -> 0.30f
            2 -> 0.40f
            3 -> 0.50f
            4 -> 0.60f
            else -> 0.70f
        }
        val delay = (8 - lvl).coerceAtLeast(3)

        val healTotal = e.amount * ratio
        if (healTotal <= 0f) return

        val totalTicks = delay * 20
        val perTick = healTotal / totalTicks

        val list = pending.getOrPut(player.uuid) { mutableListOf() }
        list.add(PendingHeal(healTotal, totalTicks, perTick))
        STLog.log("DelayedRecovery") {
            "player=${player.name.string}, lvl=$lvl, damage=${e.amount}, ratio=$ratio, healTotal=$healTotal, " +
                "delaySeconds=$delay, totalTicks=$totalTicks, perTick=$perTick"
        }
    }

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        val p = e.player
        if (WorldSide.isClient(p.world)) return

        val list = pending[p.uuid] ?: return
        if (list.isEmpty()) return

        val iter = list.iterator()
        while (iter.hasNext()) {
            val heal = iter.next()
            if (!p.isAlive) {
                iter.remove()
                continue
            }

            val tickAmount = heal.perTick.coerceAtMost(heal.amount)
            heal.amount -= tickAmount
            heal.ticksLeft--

            p.heal(tickAmount)

            if (heal.amount <= 0.001f || heal.ticksLeft <= 0) {
                iter.remove()
            }
        }

        if (list.isEmpty()) pending.remove(p.uuid)
        else if (p.age % 5 == 0) {
            val remaining = list.sumOf { it.amount.toDouble() }
            STLog.log("DelayedRecovery") {
                "player=${p.name.string}, activeHeals=${list.size}, remaining=$remaining, " +
                    "health=${p.health}/${p.maxHealth}, outcome=payout"
            }
            (p.world as? ServerWorld)?.spawnParticles(
                ParticleTypes.HEART,
                p.x, p.y + 1.5, p.z,
                1, 0.3, 0.3, 0.3, 0.0,
            )
        }
    }

    @SubscribeEvent
    fun onDeath(e: LivingDeathEvent) {
        val player = e.entityLiving as? PlayerEntity ?: return
        if (pending[player.uuid] != null) {
            pending.remove(player.uuid)
        }
    }

    @SubscribeEvent
    fun onClone(e: PlayerEvent.Clone) {
        if (!e.wasDeath) return
        pending.remove(e.original.uuid)
    }

    @SubscribeEvent
    fun onLogout(e: PlayerEvent.PlayerLoggedOutEvent) {
        pending.remove(e.player.uuid)
    }
}
