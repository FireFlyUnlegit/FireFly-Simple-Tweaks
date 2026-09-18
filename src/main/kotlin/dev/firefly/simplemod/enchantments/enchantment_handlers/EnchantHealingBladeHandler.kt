package dev.firefly.simplemod.enchantments.enchantment_handlers

import dev.firefly.simplemod.core.Listenable
import dev.firefly.simplemod.enchantments.EnchantHealingBlade
import dev.firefly.simplemod.util.getItemSpecificEnchantLevel
import dev.firefly.simplemod.util.invalid
import net.minecraft.entity.EntityLivingBase
import net.minecraftforge.event.entity.living.LivingDamageEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent


object EnchantHealingBladeHandler : Listenable {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onLivingHurt(e: LivingDamageEvent) {
        val attacker = (e.source.trueSource?: return) as? EntityLivingBase?: return
        if (e.invalid) return
        val level = getItemSpecificEnchantLevel(attacker.heldItemMainhand, EnchantHealingBlade)
        val dmg = e.amount
        if (level > 0) {
            val healingCount = dmg * (0.05f * level)
            if (healingCount > 0.0) {
                val diff = attacker.maxHealth - attacker.health
                if (healingCount < diff) {
                    attacker.health += healingCount
                } else {
                    val maxAbsorption = attacker.maxHealth * (0.2f + (level * 0.1f))
                    val healingCount2 = diff
                    attacker.health += healingCount2
                    if (attacker.absorptionAmount < maxAbsorption) {
                        attacker.absorptionAmount = (healingCount - healingCount2 + attacker.absorptionAmount).coerceAtMost(maxAbsorption)
                    }
                }
                e.amount += healingCount
            }
        }
    }
}