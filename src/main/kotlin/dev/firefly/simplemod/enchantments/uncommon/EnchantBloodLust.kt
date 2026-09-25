package dev.firefly.simplemod.enchantments.uncommon

import dev.firefly.simplemod.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantments

object EnchantBloodLust :
    ModEnchantments(
        "bloodlust",
        ModEnchantmentType.WEAPON,
        5,
        {25 + 3 * it},
        EnchantmentCategories.UNCOMMON
    )