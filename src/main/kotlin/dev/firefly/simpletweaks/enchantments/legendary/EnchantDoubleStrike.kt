package dev.firefly.simpletweaks.enchantments.legendary

import dev.firefly.simpletweaks.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantments

object EnchantDoubleStrike : ModEnchantments(
    "double_strike",
    ModEnchantmentType.SWORD,
    6,
    { 29 + 4 * it },
    EnchantmentCategories.LEGENDARY
    )