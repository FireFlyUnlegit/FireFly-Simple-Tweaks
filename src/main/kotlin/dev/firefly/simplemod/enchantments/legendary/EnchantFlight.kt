package dev.firefly.simplemod.enchantments.legendary

import dev.firefly.simplemod.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantments
import net.minecraftforge.fml.common.Mod

@Mod.EventBusSubscriber

object EnchantFlight : ModEnchantments(
    "flight",
    ModEnchantmentType.CHESTPLATE,
    3,
    { 30 + 10 * it},
    EnchantmentCategories.LEGENDARY
)