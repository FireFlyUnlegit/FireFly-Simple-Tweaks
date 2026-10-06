package dev.firefly.simpletweaks.enchantments.epic

import dev.firefly.simpletweaks.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantments

object EnchantDelayedRecovery : ModEnchantments(
    id = "delayed_recovery",
    modType = ModEnchantmentType.ARMOR,
    enchantmentMaxLevel = 5,
    minAbility = { 25 + it * 12 },
    category = EnchantmentCategories.EPIC,
)