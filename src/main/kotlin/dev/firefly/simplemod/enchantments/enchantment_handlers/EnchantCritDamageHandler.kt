package dev.firefly.simplemod.enchantments.enchantment_handlers

import dev.firefly.simplemod.core.Listenable
import dev.firefly.simplemod.enchantments.EnchantCritDamage
import dev.firefly.simplemod.util.getItemSpecificEnchantLevel
import dev.firefly.simplemod.util.invalid
import dev.firefly.simplemod.util.isCrit
import net.minecraftforge.event.entity.player.CriticalHitEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object EnchantCritDamageHandler : Listenable {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onCrit(e: CriticalHitEvent) {
        if (e.invalid) return

        val lvl = getItemSpecificEnchantLevel(e.entityLiving.heldItemMainhand, EnchantCritDamage.INSTANCE)
        if (lvl > 0 && e.isCrit) {
            e.damageModifier += (.1 * lvl).toFloat()
            
            if (lvl >= 2) e.damageModifier += (.05f * (lvl - 2))
            if (lvl >= 6) e.damageModifier += (.025f * (lvl - 6))
        }
    }
}