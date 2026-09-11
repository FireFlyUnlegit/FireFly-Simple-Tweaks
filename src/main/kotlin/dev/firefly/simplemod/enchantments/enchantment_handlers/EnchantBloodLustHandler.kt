package dev.firefly.simplemod.enchantments.enchantment_handlers

import dev.firefly.simplemod.core.Listenable
import dev.firefly.simplemod.enchantments.EnchantBloodLust
import dev.firefly.simplemod.util.getItemSpecificEnchantLevel
import dev.firefly.simplemod.util.isClientSide
import net.minecraft.entity.EntityLivingBase
import net.minecraftforge.event.entity.living.LivingHurtEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object EnchantBloodLustHandler : Listenable {
    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.isClientSide) return
        val source = e.source
        val attacker = source.trueSource?: return
        val stack = (attacker as? EntityLivingBase)?.heldItemMainhand?: return
        val level = getItemSpecificEnchantLevel(stack, EnchantBloodLust.INSTANCE)
        if (level > 0) {
            val ratio = (attacker.maxHealth - attacker.health) / attacker.maxHealth
            val bonus = ratio * (0.005 + ((level-1) * 0.0025))
            if (bonus > 0.0) {
                e.amount = (e.amount + e.amount*bonus).toFloat()
            }
        }
    }
}