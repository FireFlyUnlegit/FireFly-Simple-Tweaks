package dev.firefly.simplemod.enchantments.enchantment_handlers

import dev.firefly.simplemod.core.Listenable
import dev.firefly.simplemod.enchantments.EnchantMotionBonus
import dev.firefly.simplemod.util.attacker
import dev.firefly.simplemod.util.getItemSpecificEnchantLevel
import dev.firefly.simplemod.util.invalid
import dev.firefly.simplemod.util.relativeSpeed
import net.minecraftforge.event.entity.living.LivingHurtEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object EnchantMotionBonusHandler : Listenable {
    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        val attacker = e.attacker?: return
        val lvl = getItemSpecificEnchantLevel(attacker.heldItemMainhand, EnchantMotionBonus)
        if (lvl > 0) {
            e.amount *= relativeSpeed(attacker,e.entity,true).toFloat() * lvl * 5 + 1f
        }
    }
}