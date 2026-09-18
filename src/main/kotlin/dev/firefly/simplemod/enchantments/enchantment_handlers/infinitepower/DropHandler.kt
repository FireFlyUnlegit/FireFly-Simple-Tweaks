package dev.firefly.simplemod.enchantments.enchantment_handlers.infinitepower

import dev.firefly.simplemod.enchantments.EnchantInfinitePower
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.entity.item.EntityItem
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.Enchantments
import net.minecraft.item.ItemStack
import net.minecraftforge.event.entity.living.LivingDropsEvent
import net.minecraftforge.event.world.BlockEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object DropHandler {
    @SubscribeEvent
    fun onLivingDrops(event: LivingDropsEvent) {
        val source = event.source
        val trueSource = source.trueSource
        if (trueSource !is EntityPlayer) return
        val player = trueSource
        val stack = player.heldItemMainhand
        if (stack.isEmpty) return
        val level = EnchantmentHelper.getEnchantmentLevel(EnchantInfinitePower, stack)
        if (level <= 0) return

        val lootingLevel = EnchantmentHelper.getEnchantmentLevel(Enchantments.LOOTING, stack)
        val multiplier = 64 + lootingLevel

        val newDrops = mutableListOf<EntityItem>()
        for (drop in event.drops) {
            val stackCopy = drop.item.copy()
            val newCount = stackCopy.count * multiplier
            if (newCount > 0) {
                stackCopy.count = if (newCount > Int.MAX_VALUE) Int.MAX_VALUE else newCount
                val newItem = EntityItem(drop.world, drop.posX, drop.posY, drop.posZ, stackCopy)
                newItem.motionX = drop.motionX
                newItem.motionY = drop.motionY
                newItem.motionZ = drop.motionZ
                newItem.lifespan = drop.lifespan
                newDrops.add(newItem)
            }
        }
        event.drops.clear()
        event.drops.addAll(newDrops)
    }

    @SubscribeEvent
    fun onHarvestDrops(event: BlockEvent.HarvestDropsEvent) {
        val player = event.harvester
        if (player == null) return
        val stack = player.heldItemMainhand
        if (stack.isEmpty) return
        val level = EnchantmentHelper.getEnchantmentLevel(EnchantInfinitePower, stack)
        if (level <= 0) return

        val fortuneLevel = EnchantmentHelper.getEnchantmentLevel(Enchantments.FORTUNE, stack)
        val multiplier = 64 + fortuneLevel

        val newDrops = mutableListOf<ItemStack>()
        for (drop in event.drops) {
            val copy = drop.copy()
            val newCount = copy.count * multiplier
            if (newCount > 0) {
                copy.count = if (newCount > Int.MAX_VALUE) Int.MAX_VALUE else newCount
                newDrops.add(copy)
            }
        }
        event.drops.clear()
        event.drops.addAll(newDrops)
        event.dropChance = 1.0f
    }
}