package dev.firefly.simplemod.enchantments.enchantment_handlers

import dev.firefly.simplemod.core.Listenable
import dev.firefly.simplemod.enchantments.EnchantChargedStrike
import dev.firefly.simplemod.util.getItemSpecificEnchantLevel
import dev.firefly.simplemod.util.invalid
import net.minecraft.entity.player.EntityPlayer
import net.minecraftforge.event.entity.living.LivingDamageEvent
import net.minecraftforge.event.entity.living.LivingDeathEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import java.util.*

object EnchantChargedStrikeHandler : Listenable {

    private val stored = WeakHashMap<EntityPlayer, Float>()

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onLivingDamage(e: LivingDamageEvent) {
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