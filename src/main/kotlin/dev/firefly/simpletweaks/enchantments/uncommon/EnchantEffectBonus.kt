package dev.firefly.simpletweaks.enchantments.uncommon

import dev.firefly.simpletweaks.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantments

object EnchantEffectBonus : ModEnchantments(
    "effect_bonus",
    ModEnchantmentType.WEAPON,
    3,
    { 16 + 6 * it },
    EnchantmentCategories.UNCOMMON
)