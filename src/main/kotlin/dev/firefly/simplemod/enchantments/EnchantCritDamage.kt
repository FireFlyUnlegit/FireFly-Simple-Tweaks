package dev.firefly.simplemod.enchantments

import dev.firefly.simplemod.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantments

object EnchantCritDamage : ModEnchantments(
    "crit_damage",
    ModEnchantmentType.WEAPON,
    10,
    {15 + 5 *it}
)