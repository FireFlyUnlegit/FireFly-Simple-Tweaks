package dev.firefly.simpletweaks.enchantments.uncommon

import dev.firefly.simpletweaks.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantments

object EnchantRegeneration : ModEnchantments(
    "regeneration",
    ModEnchantmentType.LEGGINGS,
    8,
    { 24 + 4 * it },
    EnchantmentCategories.UNCOMMON
) {
}