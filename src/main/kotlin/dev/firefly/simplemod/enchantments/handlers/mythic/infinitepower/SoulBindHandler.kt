package dev.firefly.simplemod.enchantments.handlers.mythic.infinitepower

import dev.firefly.simplemod.enchantments.mythic.EnchantInfinitePower
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.entity.item.EntityItem
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagList
import net.minecraftforge.event.entity.player.PlayerDropsEvent
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object SoulBindHandler {
    @SubscribeEvent
    fun onPlayerDrops(event: PlayerDropsEvent) {
        val player = event.entityPlayer
        if (player.world.isRemote) return
        val drops = event.drops
        val toRemove = mutableListOf<EntityItem>()
        val savedItems = NBTTagList()

        for (drop in drops) {
            val stack = drop.item
            if (!stack.isEmpty && EnchantmentHelper.getEnchantmentLevel(EnchantInfinitePower, stack) > 0) {
                val tag = NBTTagCompound()
                stack.writeToNBT(tag)
                savedItems.appendTag(tag)
                toRemove.add(drop)
            }
        }

        drops.removeAll(toRemove)

        if (savedItems.tagCount() > 0) {
            val nbt = player.getEntityData()
            nbt.setTag("InfiniteSoulBoundItems", savedItems)
        }
    }

    @SubscribeEvent
    fun onPlayerClone(event: PlayerEvent.Clone) {
        val oldPlayer = event.original
        val newPlayer = event.entityPlayer
        if (newPlayer.world.isRemote) return

        val oldNbt = oldPlayer.getEntityData()
        if (oldNbt.hasKey("InfiniteSoulBoundItems")) {
            val list = oldNbt.getTagList("InfiniteSoulBoundItems", 10)
            if (list.tagCount() > 0) {
                for (i in 0 until list.tagCount()) {
                    val tag = list.getCompoundTagAt(i)
                    val stack = ItemStack(tag)
                    if (!stack.isEmpty) {
                        newPlayer.inventory.addItemStackToInventory(stack)
                    }
                }
                oldNbt.removeTag("InfiniteSoulBoundItems")
            }
        }
    }
}