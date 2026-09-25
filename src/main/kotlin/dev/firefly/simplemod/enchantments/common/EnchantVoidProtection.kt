package dev.firefly.simplemod.enchantments.common

import dev.firefly.simplemod.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantments

object EnchantVoidProtection : ModEnchantments(
    "void_protection",
    ModEnchantmentType.BOOTS,
    3,
    { 20 + 10 * it },
    EnchantmentCategories.COMMON
)