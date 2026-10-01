package dev.firefly.simpletweaks.enchantments.common

import dev.firefly.simpletweaks.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantments

object EnchantVoidProtection : ModEnchantments(
    "void_protection",
    ModEnchantmentType.BOOTS,
    3,
    { 20 + 10 * it },
    EnchantmentCategories.COMMON
)