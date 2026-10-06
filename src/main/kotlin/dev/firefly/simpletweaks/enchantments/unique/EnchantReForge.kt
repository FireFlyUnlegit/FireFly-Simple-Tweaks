package dev.firefly.simpletweaks.enchantments.unique

import dev.firefly.simpletweaks.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantments

object EnchantReForge : ModEnchantments(
    "reforge",
    ModEnchantmentType.BREAKABLE,
    1,
    { it },
    EnchantmentCategories.UNIQUE
) {
    override fun isTreasureEnchantment(): Boolean {
        return true
    }
}