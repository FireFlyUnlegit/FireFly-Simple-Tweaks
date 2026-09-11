package dev.firefly.simplemod.enchantments.enchantment_handlers

import dev.firefly.simplemod.core.Listenable
import dev.firefly.simplemod.enchantments.EnchantSaturation
import dev.firefly.simplemod.util.chestplate
import dev.firefly.simplemod.util.getItemSpecificEnchantLevel
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.PlayerEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.util.UUID
import kotlin.random.Random.Default.nextFloat

object EnchantSaturationHandler : Listenable {

    private val cooldown = mutableMapOf<UUID, Int>()

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        val p = e.player
        if (p.world.isRemote) return

        val lvl = getItemSpecificEnchantLevel(p.chestplate, EnchantSaturation.INSTANCE)
        val recoveryCount: Int = lvl / 8 + 1
        if (lvl <= 0) {
            cooldown.remove(p.uniqueID)
            return
        }

        val interval = (60 - 5 * lvl).coerceAtLeast(1)
        val tick = (cooldown[p.uniqueID] ?: 0) + 1
        if (tick < interval) {
            cooldown[p.uniqueID] = tick
            return
        }
        cooldown[p.uniqueID] = 0

        val rate = (0.6 + 0.05 * lvl).coerceAtMost(1.0)
        if (nextFloat() >= rate) return

        val food = p.foodStats
        if (food.needFood()) {
            food.foodLevel = (food.foodLevel + recoveryCount).coerceAtMost(20)
        } else {
            food.setFoodSaturationLevel(
                (food.saturationLevel + recoveryCount).coerceAtMost(food.foodLevel.toFloat())
            )
        }
    }
    @SubscribeEvent
    fun onPlayerLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        cooldown.remove(event.player.uniqueID)
    }

}