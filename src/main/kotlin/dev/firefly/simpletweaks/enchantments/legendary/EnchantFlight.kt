package dev.firefly.simpletweaks.enchantments.legendary

import dev.firefly.simpletweaks.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantments
import net.minecraftforge.fml.common.Mod

@Mod.EventBusSubscriber

object EnchantFlight : ModEnchantments(
    "flight",
    ModEnchantmentType.CHESTPLATE,
    3,
    { 30 + 10 * it},
    EnchantmentCategories.LEGENDARY
)