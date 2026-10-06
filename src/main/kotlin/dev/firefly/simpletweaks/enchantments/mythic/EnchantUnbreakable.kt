package dev.firefly.simpletweaks.enchantments.mythic

import dev.firefly.simpletweaks.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantments

object EnchantUnbreakable : ModEnchantments(
    "unbreakable",
    ModEnchantmentType.BREAKABLE,
    1,
    {60},
    EnchantmentCategories.MYTHIC
)