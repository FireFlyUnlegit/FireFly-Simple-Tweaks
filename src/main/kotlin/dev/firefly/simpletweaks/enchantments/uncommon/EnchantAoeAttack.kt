package dev.firefly.simpletweaks.enchantments.uncommon

import dev.firefly.simpletweaks.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantments

object EnchantAoeAttack :
    ModEnchantments(
        "aoe_attack",
        ModEnchantmentType.WEAPON,
        5,
        {25 + 5 * it},
        EnchantmentCategories.UNCOMMON
    )