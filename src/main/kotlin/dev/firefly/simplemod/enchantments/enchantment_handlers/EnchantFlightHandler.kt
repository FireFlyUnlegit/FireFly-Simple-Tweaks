package dev.firefly.simplemod.enchantments.enchantment_handlers

import dev.firefly.simplemod.core.Listenable
import dev.firefly.simplemod.enchantments.EnchantFlight
import dev.firefly.simplemod.util.getItemSpecificEnchantLevel
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.PlayerEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.util.UUID

object EnchantFlightHandler : Listenable {
    private val isControllingFlight = mutableMapOf<UUID, Boolean>()
    private val flyingTick = mutableMapOf<UUID, Int>()
    private val maxLevel = EnchantFlight.INSTANCE.maxLevel

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        val p = e.player
        val uuid = p.uniqueID
        val stack = p.inventory.armorInventory[2]
        val enchantment = EnchantFlight.INSTANCE
        val level = getItemSpecificEnchantLevel(stack, enchantment)
        if (level > 0 && p.foodStats.foodLevel > 0) {
            isControllingFlight[uuid] = true
            flyingTick[uuid] = (flyingTick[uuid] ?: 0) + 1
            p.capabilities.allowFlying = true
            if (p is EntityPlayerMP) {
                p.sendPlayerAbilities()
            }
            val tick = flyingTick[uuid] ?: 0
            if (p.capabilities.isFlying && level < maxLevel && (!p.isCreative || !p.isSpectator)) {
                if (tick % (20 * level) == 0) {
                    stack.itemDamage += 2
                }
                if (tick % (50 * level) == 0) {
                    p.foodStats.foodLevel--
                }
            }
        } else if (isControllingFlight[uuid] == true) {
            isControllingFlight[uuid] = false
            flyingTick[uuid] = 0
            p.capabilities.allowFlying = false
            p.capabilities.isFlying = false
            if (p is EntityPlayerMP) {
                p.sendPlayerAbilities()
            }
        }
    }
    @SubscribeEvent
    fun onPlayerLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        isControllingFlight.remove(event.player.uniqueID)
        flyingTick.remove(event.player.uniqueID)
    }
}