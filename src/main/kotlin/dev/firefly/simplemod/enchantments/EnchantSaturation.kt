package dev.firefly.simplemod.enchantments

import dev.firefly.simplemod.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantments

object EnchantSaturation : ModEnchantments(
    "saturation",
    ModEnchantmentType.CHESTPLATE,
    8,
    { 23 + 3 * it }
)