package dev.firefly.simpletweaks.enchantments.common

import dev.firefly.simpletweaks.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantments

object EnchantAcidAttack :
    ModEnchantments(
        "acid_attack",
        ModEnchantmentType.SWORD,
        3,
        {15 + 10 * it},
        category = EnchantmentCategories.COMMON
    )