package dev.firefly.simplemod.enchantments

import dev.firefly.simplemod.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantments

object EnchantChargedStrike : ModEnchantments(
    "charged_strike",
    ModEnchantmentType.WEAPON,
    5,
    {25 + it * 5}
)