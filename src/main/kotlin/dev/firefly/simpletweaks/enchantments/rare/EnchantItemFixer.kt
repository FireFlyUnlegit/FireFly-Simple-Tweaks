package dev.firefly.simpletweaks.enchantments.rare

import dev.firefly.simpletweaks.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantments

object EnchantItemFixer : ModEnchantments(
"item_fixer",
ModEnchantmentType.BREAKABLE,
8,
{15 + 5 * it},
    EnchantmentCategories.RARE
)