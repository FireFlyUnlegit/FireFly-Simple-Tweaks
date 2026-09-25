package dev.firefly.simplemod.enchantments.handlers.mythic.infinitepower

import dev.firefly.simplemod.enchantments.handlers.mythic.EnchantInfinitePowerHandler
import dev.firefly.simplemod.enchantments.mythic.EnchantInfinitePower
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.entity.item.EntityItem
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraftforge.event.entity.living.LivingDropsEvent
import net.minecraftforge.event.entity.player.PlayerInteractEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.network.internal.FMLNetworkHandler

object InfiniteContainerHandler {
    @SubscribeEvent
    fun onRightClickItem(event: PlayerInteractEvent.RightClickItem) {
        val player = event.entityPlayer
        if (player.world.isRemote) return
        val stack = player.heldItemMainhand
        if (stack.isEmpty) return
        val level = EnchantmentHelper.getEnchantmentLevel(EnchantInfinitePower, stack)
        if (level <= 0) return
        if (!player.isSneaking) return

        FMLNetworkHandler.openGui(player, EnchantInfinitePowerHandler.modInstance, 0, player.world, player.posX.toInt(), player.posY.toInt(), player.posZ.toInt())
        event.isCanceled = true
    }

    @SubscribeEvent
    fun onLivingDrops(event: LivingDropsEvent) {
        val source = event.source
        var player: EntityPlayer? = null

        // 获取击杀者
        if (source.trueSource is EntityPlayer) {
            player = source.trueSource as EntityPlayer
        } else if (source.immediateSource is EntityPlayer) {
            player = source.immediateSource as EntityPlayer
        }

        // 如果仍然没有，可能是远程攻击（如弓箭），尝试从 LootingLevel 推断，但这里直接返回
        if (player == null) return

        val stack = player.heldItemMainhand
        if (stack.isEmpty) return
        val level = EnchantmentHelper.getEnchantmentLevel(EnchantInfinitePower, stack)
        if (level <= 0) return

        val inv = InfiniteBagInventory(player)
        val drops = event.drops
        val toRemove = mutableListOf<EntityItem>()

        for (drop in drops) {
            val item = drop.item
            if (!item.isEmpty) {
                var merged = false
                // 尝试合并到已有堆叠
                for (i in 0 until inv.getSizeInventory()) {
                    val existing = inv.getStackInSlot(i)
                    if (!existing.isEmpty && existing.isItemEqual(item) && ItemStack.areItemStackTagsEqual(existing, item)) {
                        val total = existing.count + item.count
                        if (total <= Int.MAX_VALUE) {
                            existing.grow(item.count)
                            item.count = 0
                            toRemove.add(drop)
                            merged = true
                            break
                        } else {
                            val space = Int.MAX_VALUE - existing.count
                            existing.grow(space)
                            item.shrink(space)
                        }
                    }
                }
                // 如果没有合并完成，放入空槽
                if (!merged && !item.isEmpty) {
                    for (i in 0 until inv.getSizeInventory()) {
                        if (inv.getStackInSlot(i).isEmpty) {
                            inv.setInventorySlotContents(i, item.copy())
                            item.count = 0
                            toRemove.add(drop)
                            merged = true
                            break
                        }
                    }
                }
            }
        }

        drops.removeAll(toRemove)
        inv.markDirty()
    }
}