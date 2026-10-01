package dev.firefly.simpletweaks.enchantments.epic

import dev.firefly.simpletweaks.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantments

object EnchantChargedStrike : ModEnchantments(
    "charged_strike",
    ModEnchantmentType.WEAPON,
    5,
    {25 + it * 5},
    EnchantmentCategories.EPIC
)