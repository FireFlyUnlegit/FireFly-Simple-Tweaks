package dev.firefly.simpletweaks.enchantments.rare

import dev.firefly.simpletweaks.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantments

object EnchantExtraArmor : ModEnchantments(
    "extra_armor",
    ModEnchantmentType.ARMOR,
    2,
    {30 * it},
    EnchantmentCategories.RARE
)