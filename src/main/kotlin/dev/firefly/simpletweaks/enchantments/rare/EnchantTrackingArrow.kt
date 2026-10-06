package dev.firefly.simpletweaks.enchantments.rare

import dev.firefly.simpletweaks.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantments

object EnchantTrackingArrow : ModEnchantments(
    id = "tracking_arrow",
    modType = ModEnchantmentType.BOW,
    enchantmentMaxLevel = 5,
    minAbility = { 20 + it * 12 },
    category = EnchantmentCategories.RARE,
)