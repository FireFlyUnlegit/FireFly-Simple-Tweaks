package dev.firefly.simpletweaks.enchantments.handlers.epic

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.epic.EnchantChargedStrike
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.invalid
import net.minecraft.entity.player.EntityPlayer
import net.minecraftforge.event.entity.living.LivingDeathEvent
import net.minecraftforge.event.entity.living.LivingHurtEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import java.util.*

object EnchantChargedStrikeHandler : Listenable {

    private val stored = WeakHashMap<EntityPlayer, Float>()

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        val attacker = e.source.trueSource as? EntityPlayer ?: return

        val lvl = getItemSpecificEnchantLevel(attacker.heldItemMainhand, EnchantChargedStrike)
        if (lvl <= 0) {
            stored.remove(attacker)
            return
        }

        val currentDamage = e.amount
        val bonus = stored[attacker] ?: 0f

        if (bonus > 0f) {
            e.amount = currentDamage + bonus
        }

        stored[attacker] = currentDamage * (0.15f * lvl)
    }

    @SubscribeEvent
    fun onPlayerDeath(e: LivingDeathEvent) {
        val player = e.entityLiving as? EntityPlayer ?: return
        stored.remove(player)
    }
}