package dev.firefly.simpletweaks.enchantments.epic

import dev.firefly.simpletweaks.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantments

object EnchantVitality : ModEnchantments(
    "vitality",
    ModEnchantmentType.ARMOR,
    10,
    {it * 10},
    EnchantmentCategories.EPIC,
)