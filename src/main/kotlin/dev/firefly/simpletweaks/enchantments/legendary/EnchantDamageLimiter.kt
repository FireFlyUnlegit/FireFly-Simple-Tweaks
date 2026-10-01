package dev.firefly.simpletweaks.enchantments.legendary

import dev.firefly.simpletweaks.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantments

object EnchantDamageLimiter : ModEnchantments(
    "damage_limiter",
    ModEnchantmentType.ARMOR,
    3,
    {22 + 8 * it},
    EnchantmentCategories.LEGENDARY
)