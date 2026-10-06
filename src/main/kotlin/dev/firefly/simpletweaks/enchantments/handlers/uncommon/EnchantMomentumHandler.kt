package dev.firefly.simpletweaks.enchantments.handlers.uncommon

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.uncommon.EnchantMomentum
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.math.BlockPos
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import java.util.*

object EnchantMomentumHandler : Listenable {


    private class DigState(val pos: BlockPos) {
        var ticks: Int = 1
    }

    private val digging = WeakHashMap<EntityPlayer, DigState>()

    @SubscribeEvent(priority = EventPriority.LOW)
    fun onBreakSpeed(e: PlayerEvent.BreakSpeed) {
        val p = e.entityPlayer
        val lvl = getItemSpecificEnchantLevel(p.heldItemMainhand, EnchantMomentum)
        if (lvl <= 0) {
            digging.remove(p)
            return
        }

        val pos = e.pos ?: return
        val state = digging[p]

        if (state == null || state.pos != pos) {
            digging[p] = DigState(pos)
            return
        }

        state.ticks++

        val bonus = (0.05f * lvl * state.ticks).coerceAtMost(1f * lvl)
        e.newSpeed *= (1f + bonus)
    }

    @SubscribeEvent
    fun onLogout(e: net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent) {
        digging.remove(e.player)
    }
}