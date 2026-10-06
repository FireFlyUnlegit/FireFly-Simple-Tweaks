package dev.firefly.simpletweaks.enchantments.epic

import dev.firefly.simpletweaks.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantments

object EnchantCritDamage : ModEnchantments(
    "crit_damage",
    ModEnchantmentType.SWORD,
    10,
    {15 + 5 *it},
    EnchantmentCategories.EPIC
)