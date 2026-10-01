package dev.firefly.simpletweaks.enchantments.handlers.common

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.common.EnchantMotionBonus
import dev.firefly.simpletweaks.util.attacker
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.invalid
import dev.firefly.simpletweaks.util.relativeSpeed
import net.minecraftforge.event.entity.living.LivingHurtEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object EnchantMotionBonusHandler : Listenable {
    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        val attacker = e.attacker?: return
        val lvl = getItemSpecificEnchantLevel(attacker.heldItemMainhand, EnchantMotionBonus)
        if (lvl > 0) {
            e.amount *= (relativeSpeed(attacker,e.entity).toFloat() * lvl * 0.2f).coerceAtMost(lvl.toFloat()) + 1f
        }
    }
}