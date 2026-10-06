package dev.firefly.simpletweaks.enchantments.uncommon

import dev.firefly.simpletweaks.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantments

object EnchantResilience : ModEnchantments(
    "resilience",
    ModEnchantmentType.ARMOR,
    3,
    { it * 10 },
    EnchantmentCategories.UNCOMMON,
)