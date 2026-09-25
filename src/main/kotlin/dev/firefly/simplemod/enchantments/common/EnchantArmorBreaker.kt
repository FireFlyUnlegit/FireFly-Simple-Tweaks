package dev.firefly.simplemod.enchantments.common

import dev.firefly.simplemod.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantments

object EnchantArmorBreaker : ModEnchantments(
    "armor_breaker",
    ModEnchantmentType.WEAPON,
    3,
    {16 + 6 * it},
    EnchantmentCategories.COMMON
)