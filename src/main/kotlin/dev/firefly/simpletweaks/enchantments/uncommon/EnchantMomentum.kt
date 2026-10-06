package dev.firefly.simpletweaks.enchantments.uncommon

import dev.firefly.simpletweaks.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantments

object EnchantMomentum : ModEnchantments(
    "momentum",
    ModEnchantmentType.TOOL,
    8,
    {it * 5},
    EnchantmentCategories.UNCOMMON
)