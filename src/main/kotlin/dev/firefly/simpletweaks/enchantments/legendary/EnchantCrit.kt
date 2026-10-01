package dev.firefly.simpletweaks.enchantments.legendary

import dev.firefly.simpletweaks.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantments

object EnchantCrit : ModEnchantments(
    "crit",
    ModEnchantmentType.WEAPON,
    10,
    {15 + 5 *it},
    EnchantmentCategories.LEGENDARY
)