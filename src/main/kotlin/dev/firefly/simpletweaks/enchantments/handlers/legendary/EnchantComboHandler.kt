package dev.firefly.simpletweaks.enchantments.handlers.legendary

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.legendary.EnchantCombo
import dev.firefly.simpletweaks.util.attacker
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.invalid
import dev.firefly.simpletweaks.util.target
import net.minecraftforge.event.entity.living.LivingHurtEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object EnchantComboHandler : Listenable {
    @SubscribeEvent(priority = EventPriority.NORMAL)
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        val attacker = e.attacker ?: return
        val target = e.target

        val level = getItemSpecificEnchantLevel(attacker.heldItemMainhand, EnchantCombo)
        if (level <= 0) return

        val multiplier = 1f - level * 0.1f
        target.hurtResistantTime =
            (target.hurtResistantTime * multiplier).toInt().coerceAtLeast(0)
    }
}