package dev.firefly.simpletweaks.enchantments.epic

import dev.firefly.simpletweaks.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantments

object EnchantGravityStrike : ModEnchantments(
    "gravity_strike",
    ModEnchantmentType.SWORD,
    6,
    {it * 15},
    EnchantmentCategories.EPIC
)