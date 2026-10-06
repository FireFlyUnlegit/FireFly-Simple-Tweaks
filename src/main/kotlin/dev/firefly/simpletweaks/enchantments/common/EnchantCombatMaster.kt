package dev.firefly.simpletweaks.enchantments.common


import dev.firefly.simpletweaks.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantments

object EnchantCombatMaster : ModEnchantments(
    "combat_master",
    ModEnchantmentType.SWORD,
    5,
    { it * 8 },
    EnchantmentCategories.COMMON,
)