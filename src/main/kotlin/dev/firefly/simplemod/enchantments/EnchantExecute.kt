package dev.firefly.simplemod.enchantments

import dev.firefly.simplemod.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantments

object EnchantExecute : ModEnchantments(
    "execute",
    ModEnchantmentType.WEAPON,
    5,
    {it * 5 + 20}
)
