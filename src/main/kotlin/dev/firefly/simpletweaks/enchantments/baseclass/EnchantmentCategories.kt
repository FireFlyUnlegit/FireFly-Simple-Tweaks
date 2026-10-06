package dev.firefly.simpletweaks.enchantments.baseclass

import net.minecraft.util.text.TextFormatting
import kotlin.math.roundToInt

enum class EnchantmentCategories(val rarity: Int, val color: TextFormatting) {

    UNIQUE(-1, TextFormatting.WHITE),
    COMMON(0, TextFormatting.GRAY),
    UNCOMMON(1, TextFormatting.GREEN),
    RARE(2, TextFormatting.BLUE),
    EPIC(3, TextFormatting.LIGHT_PURPLE),
    LEGENDARY(4, TextFormatting.GOLD),
    MYTHIC(5, TextFormatting.DARK_RED),
    MYSTERY(6, TextFormatting.AQUA);

    val weight: Int
        get() = if (rarity < 0) 0 else (10 - rarity * 1.5.roundToInt()).coerceAtLeast(1)
}