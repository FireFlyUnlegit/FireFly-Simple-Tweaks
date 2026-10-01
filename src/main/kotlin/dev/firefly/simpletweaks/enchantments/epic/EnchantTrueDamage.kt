package dev.firefly.simpletweaks.enchantments.epic

import dev.firefly.simpletweaks.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantments

object EnchantTrueDamage : ModEnchantments(
    "true_damage",
    ModEnchantmentType.WEAPON,
    7,
    {it * 5 + 25},
    EnchantmentCategories.EPIC,
)