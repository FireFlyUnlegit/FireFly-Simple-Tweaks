package dev.firefly.simpletweaks.enchantments.handlers.uncommon

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.uncommon.EnchantRegeneration
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.invalid
import dev.firefly.simpletweaks.util.leggings
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.util.*
import net.minecraftforge.fml.common.gameevent.PlayerEvent as ev

object EnchantRegenerationHandler : Listenable {

    private val regenCounter = mutableMapOf<UUID, Int>()

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.invalid) return
        val p = e.player

        val lvl = getItemSpecificEnchantLevel(p.leggings, EnchantRegeneration)
        if (lvl <= 0 || p.health >= p.maxHealth) {
            regenCounter.remove(p.uniqueID)
            return
        }

        val uuid = p.uniqueID
        val interval = 25.0 - (2.5 * lvl).coerceAtLeast(5.0)
        val counter = (regenCounter[uuid] ?: 0) + 1
        if (counter >= interval) {
            val heal = maxOf(lvl / 2f, p.maxHealth * 0.00125f * lvl)
            p.heal(heal)
            regenCounter[uuid] = 0
        } else {
            regenCounter[uuid] = counter
        }
    }

    @SubscribeEvent
    fun onPlayerLoggedOut(e: ev.PlayerLoggedOutEvent) {
        regenCounter.remove(e.player.uniqueID)
    }

    @SubscribeEvent
    fun onPlayerClone(e: PlayerEvent.Clone) {
        if (!e.isWasDeath) return
        regenCounter.remove(e.original.uniqueID)
    }
}