package dev.firefly.simpletweaks.enchantments.legendary

import dev.firefly.simpletweaks.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantments

object EnchantDoubleCrit : ModEnchantments(
    "double_crit",
    ModEnchantmentType.SWORD,
    5,
    {30 + it * 10},
    EnchantmentCategories.LEGENDARY
)