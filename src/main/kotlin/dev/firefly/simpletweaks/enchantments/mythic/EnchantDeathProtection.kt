package dev.firefly.simpletweaks.enchantments.mythic

import dev.firefly.simpletweaks.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantments

object EnchantDeathProtection : ModEnchantments(
    "death_protection",
    ModEnchantmentType.CHESTPLATE,
    3,
    { 10 + 20 * it },
    EnchantmentCategories.MYTHIC
)