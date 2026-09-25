package dev.firefly.simplemod.enchantments.baseclass

import net.minecraft.util.text.TextFormatting

enum class EnchantmentCategories(val rarity: Int, val color: TextFormatting) {
    COMMON(0, TextFormatting.GRAY),
    UNCOMMON(1, TextFormatting.GREEN),
    RARE(2, TextFormatting.BLUE),
    EPIC(3, TextFormatting.LIGHT_PURPLE),
    LEGENDARY(4, TextFormatting.GOLD),
    MYTHIC(5, TextFormatting.DARK_RED),
    MYSTERY(6, TextFormatting.AQUA);

    val weight: Int
        get() = (24 - rarity * 4).coerceAtLeast(1)
}