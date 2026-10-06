package dev.firefly.simpletweaks.enchantments.mythic

import dev.firefly.simpletweaks.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantments

object EnchantEchoShield : ModEnchantments(
    id = "echo_shield",
    modType = ModEnchantmentType.ARMOR,
    enchantmentMaxLevel = 5,
    minAbility = { 25 + it * 12 },
    category = EnchantmentCategories.MYTHIC,
)