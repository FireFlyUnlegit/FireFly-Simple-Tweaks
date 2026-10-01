package dev.firefly.simpletweaks.enchantments.handlers.uncommon

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.uncommon.EnchantRegeneration
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.invalid
import dev.firefly.simpletweaks.util.leggings
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.PlayerEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.util.*

object EnchantRegenerationHandler : Listenable {
    val regenCounter = mutableMapOf<UUID, Int>()

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.invalid) return
        val p = e.player
        val lvl = getItemSpecificEnchantLevel(p.leggings, EnchantRegeneration)
        val amount = (lvl / 2).coerceAtLeast(1)
        if (lvl > 0) {
            val uuid = p.uniqueID
            if (p.health < p.maxHealth) {
                if ((regenCounter[uuid]?: 0) > 20 * (5 - lvl)) {
                    p.heal(amount.toFloat())
                    regenCounter[uuid] = 0
                }

                regenCounter[uuid] = (regenCounter[uuid]?:0) + 1
            }
        }
    }
    @SubscribeEvent
    fun onPlayerLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        regenCounter.remove(event.player.uniqueID)

    }
}
