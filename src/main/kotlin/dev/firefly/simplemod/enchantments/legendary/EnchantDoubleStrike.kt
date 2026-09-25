package dev.firefly.simplemod.enchantments.legendary

import dev.firefly.simplemod.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantments

object EnchantDoubleStrike : ModEnchantments(
    "double_strike",
    ModEnchantmentType.WEAPON,
    6,
    { 29 + 4 * it },
    EnchantmentCategories.LEGENDARY
    )