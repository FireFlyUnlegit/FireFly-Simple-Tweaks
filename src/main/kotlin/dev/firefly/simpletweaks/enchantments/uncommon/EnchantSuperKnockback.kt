package dev.firefly.simpletweaks.enchantments.uncommon

import dev.firefly.simpletweaks.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantments

object EnchantSuperKnockback : ModEnchantments(
    id = "super_knockback",
    modType = ModEnchantmentType.WEAPON,
    enchantmentMaxLevel = 3,
    minAbility = {it * 10},
    EnchantmentCategories.UNCOMMON
)