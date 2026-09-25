package dev.firefly.simplemod.enchantments.handlers.legendary

import dev.firefly.simplemod.core.Listenable
import dev.firefly.simplemod.enchantments.legendary.EnchantCrit
import dev.firefly.simplemod.util.getItemSpecificEnchantLevel
import dev.firefly.simplemod.util.invalid
import dev.firefly.simplemod.util.setCrit
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