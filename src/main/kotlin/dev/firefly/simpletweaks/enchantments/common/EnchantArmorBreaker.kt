package dev.firefly.simpletweaks.enchantments.common

import dev.firefly.simpletweaks.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantments

object EnchantArmorBreaker : ModEnchantments(
    "armor_breaker",
    ModEnchantmentType.WEAPON,
    3,
    {16 + 6 * it},
    EnchantmentCategories.COMMON
)