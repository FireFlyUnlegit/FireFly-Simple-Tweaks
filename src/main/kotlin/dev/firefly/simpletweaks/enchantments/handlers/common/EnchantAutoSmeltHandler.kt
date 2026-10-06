package dev.firefly.simpletweaks.enchantments.handlers.common

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.common.EnchantAutoSmelt
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.init.Enchantments
import net.minecraft.item.ItemStack
import net.minecraft.item.crafting.FurnaceRecipes
import net.minecraftforge.event.world.BlockEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object EnchantAutoSmeltHandler : Listenable {

    @SubscribeEvent(priority = EventPriority.LOW)
    fun onHarvestDrops(e: BlockEvent.HarvestDropsEvent) {
        val player = e.harvester ?: return
        val tool = player.heldItemMainhand
        if (getItemSpecificEnchantLevel(tool, EnchantAutoSmelt) <= 0) return

        if (EnchantmentHelper.getEnchantmentLevel(Enchantments.SILK_TOUCH, tool) > 0) return

        val drops = e.drops
        if (drops.isEmpty()) return

        val recipes = FurnaceRecipes.instance()
        val newDrops = mutableListOf<ItemStack>()
        var totalExp = 0f

        for (drop in drops) {
            if (drop.isEmpty) continue
            val result = recipes.getSmeltingResult(drop)
            if (!result.isEmpty) {
                val smelted = result.copy()
                smelted.count = drop.count * result.count
                newDrops.add(smelted)
                totalExp += recipes.getSmeltingExperience(drop) * drop.count
                tool.damageItem(1,player)
            } else {
                newDrops.add(drop)
            }
        }

        drops.clear()
        drops.addAll(newDrops)

        if (totalExp > 0f && !player.world.isRemote) {
            val exp = totalExp.toInt()
            if (exp > 0) player.addExperience(exp)
        }
    }
}