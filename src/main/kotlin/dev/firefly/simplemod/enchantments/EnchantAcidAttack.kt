package dev.firefly.simplemod.enchantments

import dev.firefly.simplemod.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantments

object EnchantAcidAttack :
    ModEnchantments(
        "acid_attack",
        ModEnchantmentType.WEAPON,
        3,
        {15 + 10 * it}
    )