package dev.firefly.simplemod.enchantments.enchantment_handlers

import dev.firefly.simplemod.core.Listenable
import dev.firefly.simplemod.enchantments.EnchantDeathProtection
import dev.firefly.simplemod.util.chestplate
import dev.firefly.simplemod.util.getItemSpecificEnchantLevel
import dev.firefly.simplemod.util.invalid
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.text.TextComponentString
import net.minecraftforge.event.entity.living.LivingDamageEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object EnchantDeathProtectionHandler : Listenable {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onLivingDamage(e: LivingDamageEvent) {
        if (e.invalid) return
        val player = e.entityLiving as? EntityPlayer ?: return

        if (player.health - e.amount >= 0f) return

        val chest = player.chestplate
        val lvl = getItemSpecificEnchantLevel(chest, EnchantDeathProtection)
        if (lvl <= 0) return

        val absorption = lvl * 20f

        val enchants = EnchantmentHelper.getEnchantments(chest)
        if (lvl - 1 > 0) {
            enchants[EnchantDeathProtection] = lvl - 1
        } else {
            enchants.remove(EnchantDeathProtection)
        }
        EnchantmentHelper.setEnchantments(enchants, chest)

        player.absorptionAmount = absorption
        e.amount = 0f
        e.isCanceled = true

        player.sendMessage(TextComponentString("Death Protection triggered!"))
    }
}