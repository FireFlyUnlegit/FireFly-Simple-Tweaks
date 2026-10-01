package dev.firefly.simpletweaks.enchantments.handlers.uncommon

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.uncommon.EnchantSwiftSneak
import dev.firefly.simpletweaks.extraforgeapi.extraevents.SlowDownEvent
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.leggings
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object EnchantSwiftSneakHandler : Listenable {
    @SubscribeEvent
    fun onSlowDown(e: SlowDownEvent) {
        if (e.isCanceled && e.type != SlowDownEvent.Type.SNEAK) return
        val lvl = getItemSpecificEnchantLevel(e.player.leggings, EnchantSwiftSneak)
        if (lvl > 0) {
            e.speedFactor = 0.3f + 0.15f * lvl
        }
    }
}