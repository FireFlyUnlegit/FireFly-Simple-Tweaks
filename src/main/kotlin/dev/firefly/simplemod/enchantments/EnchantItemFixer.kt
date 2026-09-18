package dev.firefly.simplemod.enchantments

import dev.firefly.simplemod.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantments

object EnchantItemFixer : ModEnchantments(
"item_fixer",
ModEnchantmentType.BREAKABLE,
8,
{15 + 5 * it}
)