package dev.firefly.simpletweaks.enchantments.legendary

import dev.firefly.simpletweaks.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantments

object EnchantSoulBound : ModEnchantments(
    "soul_bound",
    ModEnchantmentType.BREAKABLE,
    1,
    { 25 },
    EnchantmentCategories.LEGENDARY
)