package dev.firefly.simplemod.enchantments.enchantment_handlers

import dev.firefly.simplemod.core.Listenable
import dev.firefly.simplemod.enchantments.EnchantAssassin
import dev.firefly.simplemod.util.getItemSpecificEnchantLevel
import dev.firefly.simplemod.util.invalid
import net.minecraft.entity.EntityLivingBase
import net.minecraftforge.event.entity.living.LivingDamageEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object EnchantAssassinHandler : Listenable {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onLivingHurt(e: LivingDamageEvent) {
        if (e.invalid) return
        val attacker = (e.source.trueSource as? EntityLivingBase)?: return
        val target = e.entityLiving?: return
        val lvl = getItemSpecificEnchantLevel( attacker.heldItemMainhand , EnchantAssassin.INSTANCE)
        if (lvl > 0) {
            val threshold = target.maxHealth * 0.01f * lvl
            val remaining = target.health - e.amount
            if (remaining < threshold) {
                e.amount = target.maxHealth + target.absorptionAmount
            }
        }
    }
}