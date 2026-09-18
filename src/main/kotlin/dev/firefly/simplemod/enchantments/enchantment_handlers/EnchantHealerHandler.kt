package dev.firefly.simplemod.enchantments.enchantment_handlers

import dev.firefly.simplemod.core.Listenable
import dev.firefly.simplemod.enchantments.EnchantHealer
import dev.firefly.simplemod.util.getItemSpecificEnchantLevel
import dev.firefly.simplemod.util.invalid
import net.minecraft.entity.EntityLivingBase
import net.minecraftforge.event.entity.living.LivingDamageEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object EnchantHealerHandler : Listenable{
    @SubscribeEvent(priority = EventPriority.LOW)
    fun onLivingHurt(e: LivingDamageEvent) {
        if (e.invalid) return
        val lvl = getItemSpecificEnchantLevel((((e.source.trueSource as? EntityLivingBase) ?:return)
            .heldItemMainhand),
            EnchantHealer)
        if (lvl > 0)
        {
            val originDMG = e.amount
            val living = e.entityLiving
            val healingFactor = 0.2f * lvl
            living.health = (originDMG * healingFactor + e.entityLiving.health).coerceAtMost(living.maxHealth)
            e.isCanceled = true
        }
    }
}

