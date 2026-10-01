package dev.firefly.simpletweaks.enchantments.epic

import dev.firefly.simpletweaks.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantments

object EnchantDamageReduction : ModEnchantments(
    "damage_reduction",
    ModEnchantmentType.ARMOR,
    5,
    {it * 10},
    EnchantmentCategories.EPIC
)