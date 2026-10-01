package dev.firefly.simpletweaks.enchantments.uncommon

import dev.firefly.simpletweaks.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantments

object EnchantBloodLust :
    ModEnchantments(
        "bloodlust",
        ModEnchantmentType.WEAPON,
        5,
        {25 + 3 * it},
        EnchantmentCategories.UNCOMMON
    )