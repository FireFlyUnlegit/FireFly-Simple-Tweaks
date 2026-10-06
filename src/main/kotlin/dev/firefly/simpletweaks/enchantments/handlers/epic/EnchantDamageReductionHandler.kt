package dev.firefly.simpletweaks.enchantments.handlers.epic

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.epic.EnchantDamageReduction
import dev.firefly.simpletweaks.util.getArmorEnchantLevel
import dev.firefly.simpletweaks.util.invalid
import dev.firefly.simpletweaks.util.target
import net.minecraftforge.event.entity.living.LivingHurtEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object EnchantDamageReductionHandler : Listenable {
    @SubscribeEvent(priority = EventPriority.HIGH)
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        val lvl = e.target.getArmorEnchantLevel(EnchantDamageReduction)
        if (lvl > 0) {
            val reduction1 = 0.024f * lvl
            val reduction2 = 0.018f * (lvl - 5).coerceAtLeast(0)
            val reduction3 = 0.012f * (lvl - 10).coerceAtLeast(0)
            val reduction4 = 0.006f * (lvl - 15).coerceAtLeast(0)
            val totalReduction = (reduction1 + reduction2 + reduction3 + reduction4).coerceAtMost(0.9f)
            e.amount = (e.amount * (1f - totalReduction) - (lvl * 0.001f)).coerceAtLeast(0f)
        }
    }
}