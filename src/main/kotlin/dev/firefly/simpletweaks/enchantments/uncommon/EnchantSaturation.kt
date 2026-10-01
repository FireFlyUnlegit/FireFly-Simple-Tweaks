package dev.firefly.simpletweaks.enchantments.uncommon

import dev.firefly.simpletweaks.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantments

object EnchantSaturation : ModEnchantments(
    "saturation",
    ModEnchantmentType.CHESTPLATE,
    8,
    { 23 + 3 * it },
    EnchantmentCategories.UNCOMMON
)