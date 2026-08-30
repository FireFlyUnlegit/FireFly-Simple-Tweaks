package dev.firefly.simplemod.enchantments.enchantment_handlers.infinitepower

import dev.firefly.simplemod.enchantments.EnchantInfinitePower
import dev.firefly.simplemod.enchantments.enchantment_handlers.EnchantInfinitePowerHandler
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraftforge.event.entity.player.PlayerInteractEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

@SideOnly(Side.CLIENT)
object ClientHandler {
    @SubscribeEvent
    fun onLeftClickEmpty(event: PlayerInteractEvent.LeftClickEmpty) {
        val player = event.entityPlayer
        if (player.world.isRemote) {
            val stack = player.heldItemMainhand
            val level = EnchantmentHelper.getEnchantmentLevel(EnchantInfinitePower.INSTANCE, stack)
            if (level <= 0) return
            val lookVec = player.lookVec
            EnchantInfinitePowerHandler.sendLaser(player, lookVec)
        }
    }
}