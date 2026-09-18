package dev.firefly.simplemod.enchantments

import dev.firefly.simplemod.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantments
import net.minecraftforge.fml.common.Mod

@Mod.EventBusSubscriber

object EnchantFlight : ModEnchantments(
    "flight",
    ModEnchantmentType.CHESTPLATE,
    3,
    { 30 + 10 * it }
)