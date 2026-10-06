package dev.firefly.simpletweaks.enchantments.mythic

import dev.firefly.simpletweaks.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantments

object EnchantKillAura : ModEnchantments(
    "kill_aura",
    ModEnchantmentType.SWORD,
    6,
    { 25 + 15 * it },
    EnchantmentCategories.MYTHIC
)