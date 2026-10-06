package dev.firefly.simpletweaks.enchantments.handlers.uncommon

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.uncommon.EnchantResilience
import dev.firefly.simpletweaks.util.getArmorEnchantLevel
import dev.firefly.simpletweaks.util.invalid
import net.minecraftforge.event.entity.living.LivingDamageEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object EnchantResilienceHandler : Listenable {

    @SubscribeEvent
    fun onLivingDamage(event: LivingDamageEvent) {
        if (event.invalid) return

        val victim = event.entityLiving ?: return
        if (victim.world.isRemote) return

        val level = victim.getArmorEnchantLevel(EnchantResilience)
        if (level <= 0) return

        val maxHealth = victim.maxHealth
        if (maxHealth <= 0f) return

        val missingRatio = (1f - victim.health / maxHealth).coerceIn(0f, 1f)
        if (missingRatio <= 0f) return

        val reduction = (0.07f * level * missingRatio / 0.9f).coerceAtMost(0.84f)

        event.amount *= (1f - reduction)
    }
}