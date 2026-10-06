package dev.firefly.simpletweaks.enchantments.rare

import dev.firefly.simpletweaks.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantments

object EnchantExperienceStealer : ModEnchantments(
    "experience_stealer",
    ModEnchantmentType.SWORD,
    8,
    {it * 5 + 20},
    EnchantmentCategories.RARE
)