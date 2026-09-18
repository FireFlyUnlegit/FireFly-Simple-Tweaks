package dev.firefly.simplemod.enchantments

import dev.firefly.simplemod.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantments

object EnchantImmortal : ModEnchantments(
    "immortal",
    ModEnchantmentType.ARMOR,
    1,
    { 30 },
)