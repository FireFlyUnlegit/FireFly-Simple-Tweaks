package dev.firefly.simplemod.enchantments.enchantment_handlers.infinitepower

import dev.firefly.simplemod.enchantments.EnchantInfinitePower
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object ToolHandler {
    @SubscribeEvent
    fun onHarvestCheck(event: PlayerEvent.HarvestCheck) {
        val player = event.entityPlayer
        val stack = player.heldItemMainhand
        val level = EnchantmentHelper.getEnchantmentLevel(EnchantInfinitePower.INSTANCE, stack)
        if (level > 0) {
            event.setCanHarvest(true)
        }
    }

    @SubscribeEvent
    fun onBreakSpeed(event: PlayerEvent.BreakSpeed) {
        val player = event.entityPlayer
        val stack = player.heldItemMainhand
        val level = EnchantmentHelper.getEnchantmentLevel(EnchantInfinitePower.INSTANCE, stack)
        if (level > 0) {
            event.newSpeed = Float.MAX_VALUE
        }
    }
}