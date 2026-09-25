package dev.firefly.simplemod.enchantments.common

import dev.firefly.simplemod.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantments

object EnchantMotionBonus : ModEnchantments(
    "motion_bonus",
    ModEnchantmentType.WEAPON,
    5,
    {15 + 4 * it},
    EnchantmentCategories.COMMON
)