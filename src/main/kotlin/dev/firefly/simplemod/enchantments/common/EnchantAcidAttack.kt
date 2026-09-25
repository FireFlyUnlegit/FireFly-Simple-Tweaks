package dev.firefly.simplemod.enchantments.common

import dev.firefly.simplemod.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantments

object EnchantAcidAttack :
    ModEnchantments(
        "acid_attack",
        ModEnchantmentType.WEAPON,
        3,
        {15 + 10 * it},
        category = EnchantmentCategories.COMMON
    )