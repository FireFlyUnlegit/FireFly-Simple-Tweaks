package dev.firefly.simpletweaks.enchantments.epic

import dev.firefly.simpletweaks.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantments

object EnchantTunneling : ModEnchantments(
    "tunneling",
    ModEnchantmentType.TOOL,
    3,
    { 20 + 10 * it },
    EnchantmentCategories.EPIC
)