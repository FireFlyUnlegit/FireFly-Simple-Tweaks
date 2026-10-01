package dev.firefly.simpletweaks.enchantments.handlers.epic

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.epic.EnchantCritDamage
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.invalid
import dev.firefly.simpletweaks.util.isCrit
import net.minecraftforge.event.entity.player.CriticalHitEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object EnchantCritDamageHandler : Listenable {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onCrit(e: CriticalHitEvent) {
        if (e.invalid) return

        val lvl = getItemSpecificEnchantLevel(e.entityLiving.heldItemMainhand, EnchantCritDamage)
        if (lvl > 0 && e.isCrit) {
            e.damageModifier += (.1 * lvl).toFloat()
            
            if (lvl >= 2) e.damageModifier += (.05f * (lvl - 2))
            if (lvl >= 6) e.damageModifier += (.025f * (lvl - 6))
        }
    }
}