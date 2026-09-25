package dev.firefly.simplemod.enchantments.legendary

import dev.firefly.simplemod.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantments

object EnchantDoubleCrit : ModEnchantments(
    "double_crit",
    ModEnchantmentType.WEAPON,
    5,
    {30 + it * 10},
    EnchantmentCategories.LEGENDARY
)