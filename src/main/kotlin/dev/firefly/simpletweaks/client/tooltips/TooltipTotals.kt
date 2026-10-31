package dev.firefly.simpletweaks.client.tooltips

import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.AttributeModifiersComponent
import net.minecraft.entity.attribute.EntityAttribute
import net.minecraft.item.ItemStack
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.text.Text
import net.minecraft.text.TranslatableTextContent

/**
 * Where a flat attribute **total** line lives: the line itself, or one of its siblings.
 *
 * `siblingIndex == -1` means the translatable is the line's own `content`.
 */
internal class TotalLine(val lineIndex: Int, val siblingIndex: Int, val content: TranslatableTextContent)

/**
 * Shared primitives for rewriting an item's flat attribute **total** line — the `equals.0` lines that
 * vanilla prints for a modifier that *is* the total (`Item.BASE_ATTACK_DAMAGE_MODIFIER_ID` /
 * `BASE_ATTACK_SPEED_MODIFIER_ID`).
 *
 * ## Why this is a separate file
 * Two client handlers need the same delicate logic: the `minecraft:damage` fold
 * ([EnchantDamageToolTipHandler]) and the `minecraft:attributes` / code-driven fold
 * ([EnchantAttributeToolTipHandler]). The project has been bitten before by two copies of one rule
 * drifting apart, so the sibling handling and the number parsing live here once.
 *
 * ## Why the totals need rewriting at all
 * `ItemStack#appendAttributeModifiersTooltip` (bytecode) reads **only**
 * `DataComponentTypes.ATTRIBUTE_MODIFIERS` — the *item's own* component — and returns early when
 * `showInTooltip()` is false. Enchantment-granted attributes and `minecraft:damage` effects are
 * applied to the entity / at hit time, so they cannot reach that number by construction. Vanilla is
 * not going to show them; the number has to be corrected on this side or not at all.
 *
 * ## The one thing that is easy to get wrong
 * 🔴 **The translatable is not always the line's own content.** For the total line,
 * `ItemStack#appendAttributeModifierTooltip` builds
 *
 * ```text
 * ScreenTexts.space().append(Text.translatable("attribute.modifier.equals." + operationId, value, name))
 * ```
 *
 * so the line's `content` is a **literal space** and the translatable is a **sibling**. A matcher that
 * only inspects `line.content` never finds it — that is exactly how the first version of the damage
 * fold failed on a real client while looking correct. One level of siblings is enough: that is how
 * deep vanilla nests here.
 *
 * ## Keys, never rendered strings
 * Every comparison goes by translation key (`attribute.modifier.*`, the attribute's own
 * `attribute.name.*`), because those are stable across languages while the rendered string is not.
 */
internal object TooltipTotals {

    /** The key of a *flat total* line. `Operation.ADD_VALUE`'s id is `0`, and base modifiers use it. */
    const val TOTAL_KEY = "attribute.modifier.equals.0"

    /** A plain flat number such as `7` or `44.02` — anything else counts as unparseable. */
    private val FLAT_NUMBER = Regex("""\d+(?:\.\d+)?""")

    /** The rendered text of an argument, whether vanilla passed a `String` or a `Text`. */
    fun numberOf(argument: Any): String = (argument as? Text)?.string ?: argument.toString()

    /**
     * Parses a flat number, or `null` when the string is not one.
     *
     * Deliberately anchored: a value like `1,000` (thousands separator) must NOT be read as `1`, so
     * anything that is not entirely a plain number is rejected instead of guessed at.
     */
    fun parseFlatNumber(raw: String): Float? {
        val trimmed = raw.trim()
        if (!FLAT_NUMBER.matches(trimmed)) return null
        return trimmed.toFloatOrNull()
    }

    /**
     * Formats a value with **vanilla's own** `DecimalFormat`, so the digits (rounding, trailing zeros,
     * locale decimal separator) are what vanilla would have printed for the same number.
     *
     * `DecimalFormat` is not thread-safe, but this is only touched from tooltip rendering, which runs
     * on the render thread — the same thread vanilla formats these lines on.
     */
    fun format(value: Float): String = AttributeModifiersComponent.DECIMAL_FORMAT.format(value)

    /** A line's own content plus each sibling's — see the class KDoc for why siblings matter. */
    fun nodes(line: Text): List<TranslatableTextContent> {
        val out = mutableListOf<TranslatableTextContent>()
        (line.content as? TranslatableTextContent)?.let { out += it }
        for (sibling in line.siblings) {
            (sibling.content as? TranslatableTextContent)?.let { out += it }
        }
        return out
    }

    /** True for any `attribute.modifier.*` line whose attribute argument is [attributeKey]. */
    fun namesAttribute(content: TranslatableTextContent, attributeKey: String): Boolean {
        if (!content.key.startsWith("attribute.modifier.")) return false
        return content.args.any { argument ->
            val argumentContent = (argument as? Text)?.content
            argumentContent is TranslatableTextContent && argumentContent.key == attributeKey
        }
    }

    /** Finds the flat total line for [attributeKey], sibling-aware. */
    fun findTotalLine(lines: List<Text>, attributeKey: String): TotalLine? {
        lines.forEachIndexed { index, line ->
            (line.content as? TranslatableTextContent)?.let {
                if (it.key == TOTAL_KEY && namesAttribute(it, attributeKey)) return TotalLine(index, -1, it)
            }
            line.siblings.forEachIndexed { siblingIndex, sibling ->
                (sibling.content as? TranslatableTextContent)?.let {
                    if (it.key == TOTAL_KEY && namesAttribute(it, attributeKey)) {
                        return TotalLine(index, siblingIndex, it)
                    }
                }
            }
        }
        return null
    }

    /**
     * Replaces the first flat number in [total]'s arguments with [merged], in place.
     *
     * Only the number is touched: the translation key, the remaining arguments and the line's style are
     * kept, so the line still renders exactly as vanilla would have rendered it, with the new value.
     *
     * @return `false` when no argument parses as a flat number, in which case nothing was changed.
     */
    fun replaceNumber(lines: MutableList<Text>, total: TotalLine, merged: String): Boolean {
        val numberIndex = total.content.args.indexOfFirst { parseFlatNumber(numberOf(it)) != null }
        if (numberIndex < 0) return false

        val args = total.content.args.toMutableList()
        args[numberIndex] = merged
        val replacement = Text.translatable(total.content.key, *args.toTypedArray())

        if (total.siblingIndex < 0) {
            lines[total.lineIndex] = replacement.setStyle(lines[total.lineIndex].style)
        } else {
            // `space().append(translatable)` wrapper: replace the inner node, keeping the wrapper (and
            // therefore the leading space and the inherited DARK_GREEN) intact.
            val parent = lines[total.lineIndex]
            val siblings = parent.siblings
            if (siblings is MutableList<Text>) {
                siblings[total.siblingIndex] = replacement
            } else {
                // Defensive: rebuild the wrapper rather than mutating a list that turned out to be
                // immutable. `MutableText`'s is mutable, so this is not expected to run.
                val rebuilt = parent.copyContentOnly().setStyle(parent.style)
                siblings.forEachIndexed { i, sibling ->
                    rebuilt.append(if (i == total.siblingIndex) replacement else sibling)
                }
                lines[total.lineIndex] = rebuilt
            }
        }
        return true
    }

    /**
     * Whether [stack] declares [attribute] in its **own** `ATTRIBUTE_MODIFIERS` component.
     *
     * This is the gate that says "an attack-damage/attack-speed total is a meaningful thing to print
     * for this item". Without it, an enchanted **book** gets a bonus line it has no business having:
     * `EnchantmentHelper.getEnchantments` switches to `STORED_ENCHANTMENTS` for
     * `Items.ENCHANTED_BOOK` (bytecode: `getEnchantmentsComponentType`), so a book's enchantments are
     * visible to these folds, while a book has no attack damage and therefore no total line to correct
     * — so the fold failed and the *fallback* line fired on every enchanted book.
     */
    fun declaresAttribute(stack: ItemStack, attribute: RegistryEntry<EntityAttribute>): Boolean {
        // `RegistryEntry.matches(RegistryEntry)` is deprecated; comparing keys is the supported form
        // (and is what the rest of this codebase uses).
        val key = attribute.key.orElse(null) ?: return false
        val component = stack.getOrDefault(
            DataComponentTypes.ATTRIBUTE_MODIFIERS,
            AttributeModifiersComponent.DEFAULT,
        )
        return component.modifiers().any { it.attribute().matchesKey(key) }
    }
}
