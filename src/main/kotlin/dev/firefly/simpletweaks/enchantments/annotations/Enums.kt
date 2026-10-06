package dev.firefly.simpletweaks.enchantments.annotations

import net.minecraft.util.Formatting

/**
 * 附魔分类。颜色是分类的属性（参照 1.12.2 的 `EnchantmentCategories(rarity, color)`）。
 */
enum class EnchantCategory(val color: EnchantColor) {
    COMMON(EnchantColor.GRAY),
    UNCOMMON(EnchantColor.GREEN),
    RARE(EnchantColor.BLUE),
    EPIC(EnchantColor.LIGHT_PURPLE),
    LEGENDARY(EnchantColor.GOLD),
    MYTHIC(EnchantColor.DARK_RED),
    MYSTERY(EnchantColor.AQUA),
    UNIQUE(EnchantColor.WHITE);
    override fun toString() = name.lowercase()
}

/**
 * Tooltip 名字颜色。
 * - [INHERIT]：用所属 [EnchantCategory] 的默认色（注解默认值）。
 * - [RAINBOW]：逐字符彩虹，运行时特殊处理，无静态 Formatting。
 * - 其他：显式指定。
 */
enum class EnchantColor(val formatting: Formatting?) {
    INHERIT(null),
    RAINBOW(null),
    GRAY(Formatting.GRAY),
    GREEN(Formatting.GREEN),
    BLUE(Formatting.BLUE),
    LIGHT_PURPLE(Formatting.LIGHT_PURPLE),
    GOLD(Formatting.GOLD),
    DARK_RED(Formatting.DARK_RED),
    AQUA(Formatting.AQUA),
    WHITE(Formatting.WHITE);

    override fun toString() = name.lowercase()
}

enum class EnchantType {
    SWORD, WEAPON, HELD, ARMOR, HELMET, CHESTPLATE,
    LEGGINGS, BOOTS, BREAKABLE, TOOL, BOW;

    override fun toString() = name.lowercase()
}

enum class EnchantSlot {
    MAINHAND, OFFHAND, ARMOR, HEAD, CHEST, LEGS, FEET, ANY, HAND;

    override fun toString() = name.lowercase()
}