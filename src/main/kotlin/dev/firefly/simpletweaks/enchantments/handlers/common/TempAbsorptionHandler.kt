package dev.firefly.simpletweaks.enchantments.handlers.common

import dev.firefly.simpletweaks.compat.event.LivingDamageEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.util.TempAbsorption

object TempAbsorptionHandler : Listenable {

    @SubscribeEvent(priority = 30)
    fun onLivingDamage(e: LivingDamageEvent) {
        if (e.invalid) return
        val victim = e.entityLiving
        val absorbed = TempAbsorption.absorb(victim, e.amount)
        if (absorbed > 0f) {
            e.amount = (e.amount - absorbed).coerceAtLeast(0f)
        }
    }
}
