package dev.firefly.simplemod.enchantments.handlers.uncommon

import dev.firefly.simplemod.core.Listenable
import dev.firefly.simplemod.enchantments.uncommon.EnchantSwiftSneak
import dev.firefly.simplemod.extraforgeapi.extraevents.SlowDownEvent
import dev.firefly.simplemod.util.getItemSpecificEnchantLevel
import dev.firefly.simplemod.util.leggings
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