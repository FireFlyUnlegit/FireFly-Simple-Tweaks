package dev.firefly.simpletweaks.enchantments.others

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantments
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraftforge.event.AnvilUpdateEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object AnvilCostHandler : Listenable {

    /** 稀有度 → 附魔成本倍率，越稀有越贵 */
    private fun multiplier(category: EnchantmentCategories): Float = when (category) {
        EnchantmentCategories.COMMON    -> 1.0f
        EnchantmentCategories.UNCOMMON  -> 1.25f
        EnchantmentCategories.RARE      -> 1.5f
        EnchantmentCategories.EPIC      -> 2.0f
        EnchantmentCategories.LEGENDARY -> 3.0f
        EnchantmentCategories.MYTHIC    -> 5.0f
        EnchantmentCategories.MYSTERY   -> 8.0f
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    fun onAnvilUpdate(e: AnvilUpdateEvent) {
        val left = e.left
        val right = e.right
        if (left.isEmpty || right.isEmpty) return

        val rightEnchants = EnchantmentHelper.getEnchantments(right)
        if (rightEnchants.isEmpty()) return

        var delta = 0
        for ((ench, rightLvl) in rightEnchants) {
            if (ench !is ModEnchantments) continue

            val leftLvl = EnchantmentHelper.getEnchantmentLevel(ench, left)
            val resultLvl = if (leftLvl == rightLvl) rightLvl + 1 else maxOf(leftLvl, rightLvl)

            val original = ench.getMinEnchantability(resultLvl)
            val desired = (original * multiplier(ench.category)).toInt()
            delta += desired - original
        }

        if (delta == 0) return
        e.cost = (e.cost + delta).coerceAtLeast(1)
    }
}