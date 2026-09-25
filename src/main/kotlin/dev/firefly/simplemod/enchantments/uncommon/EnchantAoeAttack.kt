package dev.firefly.simplemod.enchantments.uncommon

import dev.firefly.simplemod.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantments

object EnchantAoeAttack :
    ModEnchantments(
        "aoe_attack",
        ModEnchantmentType.WEAPON,
        5,
        {25 + 5 * it},
        EnchantmentCategories.UNCOMMON
    )