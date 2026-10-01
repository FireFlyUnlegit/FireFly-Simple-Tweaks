package dev.firefly.simpletweaks.enchantments.handlers.legendary

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.legendary.EnchantCrit
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.invalid
import dev.firefly.simpletweaks.util.setCrit
import net.minecraftforge.event.entity.player.CriticalHitEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import kotlin.random.Random.Default.nextFloat

object EnchantCritHandler : Listenable {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onCrit(e: CriticalHitEvent) {
        if (e.invalid) return

        val lvl = getItemSpecificEnchantLevel(e.entityLiving.heldItemMainhand, EnchantCrit)
        if (lvl > 0 && nextFloat() <= .1 * lvl && !e.isVanillaCritical) e.setCrit(true)
    }
}