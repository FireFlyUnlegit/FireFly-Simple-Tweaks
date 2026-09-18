package dev.firefly.simplemod.enchantments

import dev.firefly.simplemod.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantments

object EnchantDamageLimiter : ModEnchantments(
    "damage_limiter",
    ModEnchantmentType.ARMOR,
    3,
    {22 + 8 * it}
)