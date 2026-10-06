package dev.firefly.simpletweaks.enchantments.common

import dev.firefly.simpletweaks.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantments

object EnchantMotionBonus : ModEnchantments(
    "motion_bonus",
    ModEnchantmentType.SWORD,
    5,
    {15 + 4 * it},
    EnchantmentCategories.COMMON
)