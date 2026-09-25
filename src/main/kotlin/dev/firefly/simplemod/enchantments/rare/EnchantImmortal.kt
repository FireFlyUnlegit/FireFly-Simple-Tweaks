package dev.firefly.simplemod.enchantments.rare

import dev.firefly.simplemod.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantments

object EnchantImmortal : ModEnchantments(
    "immortal",
    ModEnchantmentType.ARMOR,
    1,
    { 30 },
    EnchantmentCategories.RARE
)