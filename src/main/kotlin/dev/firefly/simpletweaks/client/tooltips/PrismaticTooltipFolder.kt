package dev.firefly.simpletweaks.client.tooltips

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.Screen
import net.minecraft.component.EnchantmentEffectComponentTypes
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.enchantment.effect.AttributeEnchantmentEffect
import net.minecraft.item.ItemStack
import net.minecraft.text.Text
import net.minecraft.util.Formatting

/**
 * Folds `prismatic_blessing`'s **attribute block** into one line.
 *
 * ## The problem
 * The enchantment is purely declarative: 17 `minecraft:attributes` entries at `+4 %` per level. Something
 * on the client turns those into 17 real tooltip lines —
 *
 * ```text
 * 装备时：
 * +20% 最大生命值
 * +20% 速度
 * … (17 of them)
 * 在主手时：
 * ```
 *
 * — which is taller than the item's own stats block and covers the whole screen on a large GUI scale.
 * Vanilla itself does **not** draw them (`Enchantment` has no tooltip method, and
 * `ItemEnchantmentsComponent#appendTooltip` only prints names via `Enchantment#getName`), so the lines
 * come from whatever mirror these effects into the stack's `ATTRIBUTE_MODIFIERS`; that is exactly why
 * this fold is a **post-process on the finished line list** instead of a change at the source.
 *
 * ## How the run is identified (no second list of attributes)
 * The attribute list is read **from the enchantment's own `ATTRIBUTES` component in the live registry**,
 * then each attribute's display name is resolved through `Text.translatable(translationKey).string` —
 * i.e. the same strings the offending lines show, in whatever language the client is running. A line
 * participates when it contains one of those names *and* a `%`. Nothing here hardcodes
 * "max_health, movement_speed, …", so adding an 18th attribute to the handler keeps working, and a
 * weapon's own `+8 护甲` line (no `%`, and named by the same word "护甲值"? no — flat lines carry no
 * percent) is never touched.
 *
 * ## Why Shift expands instead of a config toggle
 * The information is useful, the *height* is the problem. Holding Shift is the established convention
 * for "show me the details" in item tooltips, it needs no config key, and it keeps the default view
 * readable. Shift state is read from `Screen.hasShiftDown()`, which is also what vanilla uses for its own
 * "hold shift" hint.
 *
 * ## Safety
 * - Only for a stack that actually carries `prismatic_blessing` with level > 0.
 * - Requires **at least 3** matching lines; otherwise nothing is touched (a partial match would be a
 *   rendering change nobody asked for).
 * - Debug probe `[FST-PrismaticTooltip]` reports `matched=` and `outcome=folded|expanded|too-few`.
 */
object PrismaticTooltipFolder {

    /** Header line of the folded block, `%1$s` = the per-line percentage, `%2$s` = number of attributes. */
    private const val FOLDED_KEY = "gui.simple_tweaks.prismatic.folded"

    /** Fewer matches than this and the block is left alone. */
    private const val MIN_MATCHES = 3

    fun register() {
        ItemTooltipCallback.EVENT.register { stack, _, _, lines ->
            if (stack.isEmpty) return@register

            val level = prismaticLevel(stack)
            if (level <= 0) return@register

            val percent = perLinePercent(stack, level) ?: return@register
            val names = attributeNames(stack)
            if (names.isEmpty()) return@register

            val matched = mutableListOf<Int>()
            lines.forEachIndexed { index, line ->
                val text = line.string
                if (!text.contains('%')) return@forEachIndexed
                if (names.any { text.contains(it) }) matched += index
            }

            // `expanded` is reported before the "too few" case so the log can tell "the player held
            // Shift" from "the lines were not there", which look identical from outside.
            if (Screen.hasShiftDown()) {
                FSTLog.log("PrismaticTooltip") {
                    "level=$level, matched=${matched.size}, outcome=expanded"
                }
                return@register
            }
            if (matched.size < MIN_MATCHES) {
                FSTLog.log("PrismaticTooltip") {
                    "level=$level, matched=${matched.size}, outcome=too-few-to-fold"
                }
                return@register
            }

            val first = matched.first()
            // Remove from the back so the earlier indices stay valid.
            for (index in matched.asReversed()) lines.removeAt(index)
            lines.add(
                first,
                Text.translatable(FOLDED_KEY, TooltipTotals.format(percent), matched.size)
                    .formatted(Formatting.AQUA),
            )

            FSTLog.log("PrismaticTooltip") {
                "level=$level, matched=${matched.size}, percent=${TooltipTotals.format(percent)}, " +
                    "outcome=folded"
            }
        }
    }

    /** `prismatic_blessing` level on [stack], or `0`. */
    private fun prismaticLevel(stack: ItemStack): Int {
        val enchantments = EnchantmentHelper.getEnchantments(stack)
        if (enchantments.isEmpty) return 0
        for (entry in enchantments.enchantments) {
            if (entry.matchesKey(GeneratedEnchantments.PRISMATIC_BLESSING)) {
                return enchantments.getLevel(entry)
            }
        }
        return 0
    }

    /**
     * The percentage every folded line shows, read from the enchantment's own first attribute effect at
     * [level] (all 17 entries share one `amount`, so one read is enough) — never a hardcoded `4`.
     */
    private fun perLinePercent(stack: ItemStack, level: Int): Float? {
        val effects = prismaticAttributes(stack) ?: return null
        val amount = effects.firstOrNull()?.amount()?.getValue(level) ?: return null
        return (amount * 100f).takeIf { it > 0f }
    }

    /**
     * Display names of the attributes this enchantment touches, in the client's language.
     *
     * Resolved through the translation key the tooltip lines themselves use, so the match survives
     * language changes; an unresolved key comes back as the key itself, which is why the caller only
     * requires one usable name per line.
     */
    private fun attributeNames(stack: ItemStack): List<String> {
        val effects = prismaticAttributes(stack) ?: return emptyList()
        return effects
            .map { Text.translatable(it.attribute().value().translationKey).string }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    private fun prismaticAttributes(stack: ItemStack): List<AttributeEnchantmentEffect>? {
        val enchantments = EnchantmentHelper.getEnchantments(stack)
        for (entry in enchantments.enchantments) {
            if (!entry.matchesKey(GeneratedEnchantments.PRISMATIC_BLESSING)) continue
            return entry.value().effects().get(EnchantmentEffectComponentTypes.ATTRIBUTES) ?: return null
        }
        return null
    }
}
