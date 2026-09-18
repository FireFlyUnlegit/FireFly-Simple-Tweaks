package dev.firefly.simplemod.enchantments

import dev.firefly.simplemod.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantments
import net.minecraft.util.text.TextFormatting

object EnchantHealingBlade : ModEnchantments(
    "healing_blade",
    ModEnchantmentType.WEAPON,
    8,
    {25 + 8 * it},
    textColor = TextFormatting.YELLOW
)