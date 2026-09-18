package dev.firefly.simplemod.enchantments

import dev.firefly.simplemod.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantments

object EnchantAssassin :
    ModEnchantments(
        "assassin",
        ModEnchantmentType.WEAPON,
        5,
        {20 + 10 * it}
    )