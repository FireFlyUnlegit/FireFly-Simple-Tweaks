package dev.firefly.simpletweaks.enchantments.rare

import dev.firefly.simpletweaks.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantments

object EnchantAssassin :
    ModEnchantments(
        "assassin",
        ModEnchantmentType.SWORD,
        5,
        {20 + 10 * it},
        EnchantmentCategories.RARE
    )