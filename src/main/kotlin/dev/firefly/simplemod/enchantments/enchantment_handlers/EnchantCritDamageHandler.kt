package dev.firefly.simplemod.enchantments.enchantment_handlers

import dev.firefly.simplemod.core.Listenable
import dev.firefly.simplemod.enchantments.EnchantCritDamage
import dev.firefly.simplemod.util.getItemSpecificEnchantLevel
import net.minecraftforge.event.entity.player.CriticalHitEvent
import net.minecraftforge.fml.common.eventhandler.Event
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object EnchantCritDamageHandler : Listenable {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onCrit(e: CriticalHitEvent) {
        if (e.entity.world.isRemote) return

        val lvl = getItemSpecificEnchantLevel(e.entityLiving.heldItemMainhand, EnchantCritDamage.INSTANCE)
        if (lvl > 0 && (e.result == Event.Result.ALLOW || e.isVanillaCritical)) {
            e.damageModifier += (.1 * lvl).toFloat()
        }
    }
}