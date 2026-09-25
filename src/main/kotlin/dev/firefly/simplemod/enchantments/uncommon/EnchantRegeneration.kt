package dev.firefly.simplemod.enchantments.uncommon

import dev.firefly.simplemod.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantments

object EnchantRegeneration : ModEnchantments(
    "regeneration",
    ModEnchantmentType.LEGGINGS,
    4,
    { 24 + 4 * it },
    EnchantmentCategories.UNCOMMON
) {
}