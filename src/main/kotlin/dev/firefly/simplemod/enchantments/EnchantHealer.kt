package dev.firefly.simplemod.enchantments

import dev.firefly.simplemod.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantments
import net.minecraft.util.text.TextFormatting

object EnchantHealer : ModEnchantments(
    "healer",
    ModEnchantmentType.WEAPON,
    5,
    {it * 11 + 20},
    textColor = TextFormatting.GREEN
)