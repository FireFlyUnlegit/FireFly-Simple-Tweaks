package dev.firefly.simpletweaks.enchantments.mythic

import dev.firefly.simpletweaks.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantments

object EnchantPrismaticBlessing : ModEnchantments(
    id = "prismatic_blessing",
    modType = ModEnchantmentType.BREAKABLE,
    enchantmentMaxLevel = 5,
    minAbility = { 30 + it * 15 },
    category = EnchantmentCategories.MYTHIC,
)