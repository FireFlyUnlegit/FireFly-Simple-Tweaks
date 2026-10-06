package dev.firefly.simpletweaks.enchantments.handlers.epic

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.epic.EnchantDelayedRecovery
import dev.firefly.simpletweaks.util.getArmorEnchantLevel
import dev.firefly.simpletweaks.util.invalid
import dev.firefly.simpletweaks.util.target
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.EnumParticleTypes
import net.minecraft.world.WorldServer
import net.minecraftforge.event.entity.living.LivingDamageEvent
import net.minecraftforge.event.entity.living.LivingDeathEvent
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.util.*
import net.minecraftforge.fml.common.gameevent.PlayerEvent as Event1

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
        val player = e.target as? EntityPlayer ?: return

        val lvl = player.getArmorEnchantLevel(EnchantDelayedRecovery)
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

        val list = pending.getOrPut(player.uniqueID) { mutableListOf() }
        list.add(PendingHeal(healTotal, totalTicks, perTick))
    }

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        val p = e.player
        if (p.world.isRemote) return

        val list = pending[p.uniqueID] ?: return
        if (list.isEmpty()) return

        val iter = list.iterator()
        while (iter.hasNext()) {
            val heal = iter.next()
            if (!p.isEntityAlive) {
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

        if (list.isEmpty()) pending.remove(p.uniqueID)
        else if (p.ticksExisted % 5 == 0) {
            (p.world as? WorldServer)?.spawnParticle(
                EnumParticleTypes.HEART,
                p.posX, p.posY + 1.5, p.posZ,
                1, 0.3, 0.3, 0.3, 0.0,
            )
        }
    }

    @SubscribeEvent
    fun onDeath(e: LivingDeathEvent) {
        val player = e.entityLiving as? EntityPlayer ?: return
        if (pending[player.uniqueID] != null) {
            pending.remove(player.uniqueID)
        }
    }

    @SubscribeEvent
    fun onClone(e: PlayerEvent.Clone) {
        if (!e.isWasDeath) return
        pending.remove(e.original.uniqueID)
    }

    @SubscribeEvent
    fun onLogout(e: Event1.PlayerLoggedOutEvent) {
        pending.remove(e.player.uniqueID)
    }
}