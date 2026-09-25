package dev.firefly.simplemod.enchantments.rare

import dev.firefly.simplemod.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantments

object EnchantAssassin :
    ModEnchantments(
        "assassin",
        ModEnchantmentType.WEAPON,
        5,
        {20 + 10 * it},
        EnchantmentCategories.RARE
    )