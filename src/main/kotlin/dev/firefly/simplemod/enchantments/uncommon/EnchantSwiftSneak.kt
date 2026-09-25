package dev.firefly.simplemod.enchantments.uncommon

import dev.firefly.simplemod.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantments

object EnchantSwiftSneak : ModEnchantments(
    "swift_sneak",
    ModEnchantmentType.LEGGINGS,
    6,
    {12 + 4 * it},
    EnchantmentCategories.UNCOMMON
)