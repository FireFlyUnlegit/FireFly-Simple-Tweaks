package dev.firefly.simplemod.enchantments.uncommon

import dev.firefly.simplemod.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantments

object EnchantEffectBonus : ModEnchantments(
    "effect_bonus",
    ModEnchantmentType.WEAPON,
    3,
    { 16 + 6 * it },
    EnchantmentCategories.UNCOMMON
)