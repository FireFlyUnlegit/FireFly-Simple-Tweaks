package dev.firefly.simpletweaks.enchantments.common

import dev.firefly.simpletweaks.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantments

object EnchantAutoSmelt : ModEnchantments(
    id = "auto_smelt",
    modType = ModEnchantmentType.TOOL,
    enchantmentMaxLevel = 1,
    minAbility = { 20 },
    category = EnchantmentCategories.COMMON,
)