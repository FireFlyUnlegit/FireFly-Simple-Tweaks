package dev.firefly.simpletweaks.enchantments.uncommon

import dev.firefly.simpletweaks.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantments

object EnchantSwiftSneak : ModEnchantments(
    "swift_sneak",
    ModEnchantmentType.LEGGINGS,
    6,
    {12 + 4 * it},
    EnchantmentCategories.UNCOMMON
)