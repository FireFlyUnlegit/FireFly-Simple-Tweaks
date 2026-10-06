package dev.firefly.simpletweaks.enchantments.epic

import dev.firefly.simpletweaks.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantments

object EnchantMultishot : ModEnchantments(
    id = "multishot",
    modType = ModEnchantmentType.BOW,
    enchantmentMaxLevel = 5,
    minAbility = { 15 + it * 10 },
    category = EnchantmentCategories.RARE,
)