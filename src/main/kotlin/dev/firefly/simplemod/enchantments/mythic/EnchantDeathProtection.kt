package dev.firefly.simplemod.enchantments.mythic

import dev.firefly.simplemod.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantments

object EnchantDeathProtection : ModEnchantments(
    "death_protection",
    ModEnchantmentType.CHESTPLATE,
    3,
    { 10 + 20 * it },
    EnchantmentCategories.MYTHIC
)