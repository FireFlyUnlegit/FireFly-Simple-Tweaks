package dev.firefly.simpletweaks.enchantments.rare
import dev.firefly.simpletweaks.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantments

object EnchantHuntersMark : ModEnchantments(
    id = "hunters_mark",
    modType = ModEnchantmentType.BOW,
    enchantmentMaxLevel = 3,
    minAbility = { 20 + it * 10 },
    category = EnchantmentCategories.RARE,
)