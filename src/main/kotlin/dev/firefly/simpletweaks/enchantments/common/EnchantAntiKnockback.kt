package dev.firefly.simpletweaks.enchantments.common

import dev.firefly.simpletweaks.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantments

object EnchantAntiKnockback : ModEnchantments(
    "anti_knockback",
    ModEnchantmentType.ARMOR,
    2,
    {10 * it},
    EnchantmentCategories.COMMON
)