package dev.firefly.simpletweaks.enchantments.legendary

import dev.firefly.simpletweaks.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantments

object EnchantEchoShot : ModEnchantments(
    id = "echo_shot",
    modType = ModEnchantmentType.BOW,
    enchantmentMaxLevel = 5,
    minAbility = { 25 + it * 12 },
    category = EnchantmentCategories.LEGENDARY,
)