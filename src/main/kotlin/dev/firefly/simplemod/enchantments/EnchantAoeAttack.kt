package dev.firefly.simplemod.enchantments

import dev.firefly.simplemod.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantments

object EnchantAoeAttack :
    ModEnchantments(
        "aoe_attack",
        ModEnchantmentType.WEAPON,
        5,
        {25 + 5 * it}
    )