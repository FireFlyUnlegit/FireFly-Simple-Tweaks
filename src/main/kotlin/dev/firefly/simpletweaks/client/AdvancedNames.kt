package dev.firefly.simpletweaks.client

import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import net.minecraft.text.Text
import net.minecraft.util.Formatting

/** `Xxx [进阶]`: one colour throughout — the base rarity colour, suffix included. */
object AdvancedNames {
    const val PREFIX = "advanced_"

    @JvmStatic
    fun basePath(path: String): String? = if (path.startsWith(PREFIX)) path.substring(PREFIX.length) else null

    @JvmStatic
    fun name(basePath: String, level: Int, maxLevel: Int): Text {
        val colour = GeneratedEnchantments.CATEGORY[basePath]?.color?.formatting ?: Formatting.WHITE
        val text = Text.empty()
            .append(Text.translatable("enchantment.simple_tweaks.$basePath").formatted(colour))
            .append(Text.translatable("gui.simple_tweaks.advanced_suffix").formatted(colour))
        if (maxLevel > 1 && level > 0) {
            text.append(Text.literal(" ")).append(Text.translatable("enchantment.level.$level").formatted(colour))
        }
        return text
    }
}