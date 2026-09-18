package dev.firefly.simplemod.enchantments

import dev.firefly.simplemod.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantments

object EnchantVoidProtection : ModEnchantments(
    "void_protection",
    ModEnchantmentType.BOOTS,
    3,
    { 20 + 10 * it }
)