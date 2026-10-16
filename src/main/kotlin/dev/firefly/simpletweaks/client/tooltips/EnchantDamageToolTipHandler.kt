package dev.firefly.simpletweaks.client.tooltips

import dev.firefly.simpletweaks.SimpleTweaks
import dev.firefly.simpletweaks.compat.FSTLog
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.minecraft.component.EnchantmentEffectComponentTypes
import net.minecraft.component.type.AttributeModifiersComponent
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.item.ItemStack
import net.minecraft.text.Text
import net.minecraft.text.TranslatableTextContent
import net.minecraft.util.Formatting
import net.minecraft.util.math.random.Random
import kotlin.math.roundToInt

/**
 * Folds this mod's enchantment damage into the **vanilla attack-damage total line**, so a sword reads
 *
 * ```text
 * 在主手时：
 * 44.02 攻击伤害      <- vanilla's own line, rewritten from `7`
 * 1.6 攻击速度
 * ```
 *
 * ## Why the number needs rewriting at all
 * `@ModEnchantment.damagePerLevel` generates a **`minecraft:damage` enchantment effect**. Vanilla
 * applies it at hit time (`EnchantmentHelper#getDamage` feeds a `MutableFloat` through every DAMAGE
 * entry's `EnchantmentValueEffect#apply`) — it is not an item or entity attribute modifier, and the
 * held-item block is computed from the item's `ATTRIBUTE_MODIFIERS` component, so the enchantment
 * damage never had a way into that number (1.12.2's `calcDamageByCreature` was the same).
 *
 * ## How the fold works
 * The total line is `Text.translatable("attribute.modifier.equals.0", <total>, <attribute name>)`.
 * This handler:
 *  1. finds that line — a flat (`equals.0`) modifier line whose arguments name the attack-damage
 *     attribute, i.e. **vanilla's own "7 攻击伤害"**, never the enchantment block's `+20%` lines;
 *  2. parses the numeric argument (whichever argument parses — the order is not assumed);
 *  3. re-renders the *same translation key* with `total + enchantment damage`, keeping the line's
 *     original style, and replaces the entry **in place**.
 *
 * The value comes from the live registry entry (`Enchantment#effects().get(DAMAGE)` →
 * `EnchantmentLevelBasedValue#getValue(level)`, evaluated at an input of `0`, which for the
 * `minecraft:add`/`linear` shape the KSP processor emits *is* the added damage). Nothing is
 * recomputed from a client-side copy of `damagePerLevel`, so a change to the tier curve is reflected
 * automatically. Vanilla's own arithmetic is never re-implemented: the displayed total is read back
 * and only the enchantment's share is added.
 *
 * ## What this means, precisely
 * The number becomes "what a hit does **to a living entity**" rather than "the item's attribute
 * total": the enchantment damage is added on hit, so against a mob the two agree. Against something
 * the enchantment does not damage (breaking a block, a target the damage effect's requirements
 * exclude) the tooltip is optimistic. That is the author's explicit request; the alternative — a
 * separate line — is the fallback below.
 *
 * ## Fallbacks (never lose the information, never print a wrong number)
 * A **separate `+X 攻击伤害` line** is added instead when the fold is not possible:
 *  - no flat attack-damage total line exists (creative-search / recipe-book tooltips, items whose
 *    attack damage is shown as a percentage);
 *  - the numeric argument does not parse as a plain flat number (a locale that formats thousands with
 *    separators would otherwise be read as `1` instead of `1,000` — so anything with a separator is
 *    treated as unparseable rather than guessed at).
 *
 * ## Deliberate restrictions
 * 1. **This mod's namespace only** for the damage sum — another mod's damage effect may be conditional
 *    in ways its `requirements` do not capture.
 * 2. **Entries carrying `requirements` are skipped** (vanilla Sharpness/Smite/Bane are mob-type
 *    conditional; a tooltip has no `LootContext` to evaluate that with).
 * 3. **Position and identification go by translation key, never by rendered string** — the keys
 *    (`attribute.modifier.*`, `item.modifiers.*`) and the attack-damage attribute's own key (read from
 *    the registry: 1.21.1 spells it `attribute.name.generic.attack_damage`) are stable across
 *    languages. `EnchantmentNameColorMixin`'s KDoc records the same reasoning.
 *
 * ## The alternative that was NOT taken
 * Declaring the bonus as `attributes = [AttributeSpec("minecraft:generic.attack_damage", …)]` makes
 * vanilla render it as its own `+X 攻击伤害` line in the enchantment's block (that is where the
 * `+20%` lines in the screenshots come from) — but it appears *in addition to* the item total rather
 * than inside it, and it changes how the damage is applied (attribute modifiers, not the enchantment
 * damage effect). Writing a real item `ATTRIBUTE_MODIFIERS` component would change the total honestly
 * but would double-count unless the enchantment effect is removed.
 */
object EnchantDamageToolTipHandler {

    /** Satisfies the value-effect signature; the additive effect never reads it. */
    private val RANDOM: Random = Random.create()

    /**
     * The attack-damage attribute's translation key, read from the registry rather than typed out.
     *
     * Hardcoding it is how a tooltip ends up printing a raw key: 1.21.1's key is
     * `attribute.name.generic.attack_damage`, *not* the `attribute.name.attack_damage` one might
     * expect from the de-prefixed names elsewhere in this version.
     */
    private val ATTACK_DAMAGE_NAME: String = EntityAttributes.GENERIC_ATTACK_DAMAGE.value().translationKey

    /** A plain flat number such as `7` or `44.02` — anything else is treated as unparseable. */
    private val FLAT_NUMBER = Regex("""\d+(?:\.\d+)?""")

    fun register() {
        ItemTooltipCallback.EVENT.register { stack, _, _, lines ->
            if (stack.isEmpty) return@register

            val bonus = enchantmentDamageBonus(stack)
            if (bonus <= 0f) return@register

            if (foldIntoVanillaTotal(lines, bonus)) return@register

            // See the fallbacks section in the KDoc: information is never silently dropped.
            lines.add(insertionIndex(lines), bonusLine(bonus))

            // …and a failure is never silent either. The first version of this file only logged its
            // success path, so a real client failure produced a tooltip line with no explanation of
            // why; the cause (vanilla wraps the translatable in a *sibling*) then had to be dug out of
            // bytecode. This dump — debug-only, so silent in play — makes one hover enough.
            FSTLog.log("EnchantDamageTooltip") {
                "bonus=$bonus, outcome=fold-failed-fallback-line, lines=" +
                    lines.take(24).mapIndexed { i, line -> "$i:${describe(line)}" }.joinToString(" ")
            }
        }
    }

    /** One line of the diagnostic dump: its own translatable key plus any sibling keys. */
    private fun describe(line: Text): String {
        val own = (line.content as? TranslatableTextContent)?.key ?: "literal"
        val siblings = line.siblings.mapNotNull { (it.content as? TranslatableTextContent)?.key }
        return if (siblings.isEmpty()) own else "$own+[${siblings.joinToString(",")}]"
    }

    /**
     * Rewrites vanilla's attack-damage total in place. Returns `false` when that is not possible (see
     * the class KDoc), in which case the caller adds its own line.
     */
    private fun foldIntoVanillaTotal(lines: MutableList<Text>, bonus: Float): Boolean {
        val target = findAttackDamageTotal(lines) ?: return false

        val content = target.content
        val numberIndex = content.args.indexOfFirst { parseFlatNumber(numberOf(it)) != null }
        if (numberIndex < 0) return false
        val current = parseFlatNumber(numberOf(content.args[numberIndex])) ?: return false

        val merged = formatNumber(current + bonus)
        val args = content.args.toMutableList()
        args[numberIndex] = merged
        val replacement = Text.translatable(content.key, *args.toTypedArray())

        if (target.siblingIndex < 0) {
            lines[target.lineIndex] = replacement.setStyle(lines[target.lineIndex].style)
        } else {
            // `space().append(translatable)` wrapper: replace the inner node, keeping the wrapper (and
            // therefore the leading space and the inherited DARK_GREEN) intact.
            val parent = lines[target.lineIndex]
            val siblings = parent.siblings
            if (siblings is MutableList<Text>) {
                siblings[target.siblingIndex] = replacement
            } else {
                // Defensive: rebuild the wrapper rather than mutating a list that turned out to be
                // immutable. `MutableText`'s is mutable, so this is not expected to run.
                val rebuilt = parent.copyContentOnly().setStyle(parent.style)
                siblings.forEachIndexed { i, sibling ->
                    rebuilt.append(if (i == target.siblingIndex) replacement else sibling)
                }
                lines[target.lineIndex] = rebuilt
            }
        }

        FSTLog.log("EnchantDamageTooltip") {
            "total=${formatNumber(current)}, enchantmentBonus=$bonus, shown=$merged, " +
                "line=${target.lineIndex}, sibling=${target.siblingIndex}, outcome=folded-into-vanilla-total"
        }
        return true
    }

    /** Where the attack-damage total lives: the line itself, or one of its siblings. */
    private class TotalLine(val lineIndex: Int, val siblingIndex: Int, val content: TranslatableTextContent)

    /**
     * Finds vanilla's flat attack-damage total.
     *
     * ⚠️ **The translatable is not always the line's own content.** `ItemStack#appendAttributeModifierTooltip`
     * (bytecode) builds the `equals` line as
     * `ScreenTexts.space().append(Text.translatable("attribute.modifier.equals." + operationId, …))`,
     * i.e. the line's `content` is a **literal space** and the translatable is a **sibling**. A matcher
     * that only inspects `line.content` therefore never finds it — which is exactly how the first
     * version of this file failed on a real client while looking correct. One level of siblings is
     * enough: that is how deep vanilla nests here.
     */
    private fun findAttackDamageTotal(lines: List<Text>): TotalLine? {
        lines.forEachIndexed { index, line ->
            (line.content as? TranslatableTextContent)?.let {
                if (isAttackDamageTotal(it)) return TotalLine(index, -1, it)
            }
            line.siblings.forEachIndexed { siblingIndex, sibling ->
                (sibling.content as? TranslatableTextContent)?.let {
                    if (isAttackDamageTotal(it)) return TotalLine(index, siblingIndex, it)
                }
            }
        }
        return null
    }

    /** True for the *flat total* key (`attribute.modifier.equals.0`) of the attack-damage attribute. */
    private fun isAttackDamageTotal(content: TranslatableTextContent): Boolean {
        if (content.key != "attribute.modifier.equals.0") return false
        return namesAttackDamage(content)
    }

    /** The rendered text of an argument, whether vanilla passed a `String` or a `Text`. */
    private fun numberOf(argument: Any): String =
        (argument as? Text)?.string ?: argument.toString()

    /**
     * Parses a flat number, or `null` when the string is not one.
     *
     * Deliberately anchored: a value like `1,000` (thousands separator) must NOT be read as `1`, so
     * anything that is not entirely a plain number is rejected and the caller falls back to a separate
     * line.
     */
    private fun parseFlatNumber(raw: String): Float? {
        val trimmed = raw.trim()
        if (!FLAT_NUMBER.matches(trimmed)) return null
        return trimmed.toFloatOrNull()
    }

    /** Vanilla-style additive line, used only when the total cannot be rewritten. */
    private fun bonusLine(bonus: Float): Text =
        Text.translatable(
            "attribute.modifier.plus.0",
            formatNumber(bonus),
            Text.translatable(ATTACK_DAMAGE_NAME),
        ).formatted(Formatting.DARK_GREEN)

    /**
     * Where the fallback line goes: right after the last node naming the attack-damage attribute, else
     * at the end of the held-item block (before the next `item.modifiers.*` header), else at the very
     * end — in which case it is still better than no number at all.
     *
     * Sibling-aware like [findAttackDamageTotal]: with the tree walked properly, the item's own total
     * is recognised too, so the fallback lands directly under `7 攻击伤害` rather than only under the
     * enchantment block's `+20%` line.
     */
    private fun insertionIndex(lines: List<Text>): Int {
        var lastAttackDamage = -1
        var firstHeader = -1
        var nextHeader = -1

        lines.forEachIndexed { index, line ->
            for (content in translatableNodes(line)) {
                if (content.key.startsWith("item.modifiers.")) {
                    if (firstHeader < 0) firstHeader = index else if (nextHeader < 0) nextHeader = index
                }
                if (namesAttackDamage(content)) {
                    lastAttackDamage = index
                }
            }
        }

        return when {
            lastAttackDamage >= 0 -> lastAttackDamage + 1
            firstHeader >= 0 -> if (nextHeader >= 0) nextHeader else lines.size
            else -> lines.size
        }
    }

    /** A line's own content plus each sibling's — see [findAttackDamageTotal] for why. */
    private fun translatableNodes(line: Text): List<TranslatableTextContent> {
        val out = mutableListOf<TranslatableTextContent>()
        (line.content as? TranslatableTextContent)?.let { out += it }
        for (sibling in line.siblings) {
            (sibling.content as? TranslatableTextContent)?.let { out += it }
        }
        return out
    }

    /** True for any modifier line whose attribute argument is attack damage. */
    private fun namesAttackDamage(content: TranslatableTextContent): Boolean {
        if (!content.key.startsWith("attribute.modifier.")) return false
        return content.args.any { argument ->
            val argumentContent = (argument as? Text)?.content
            argumentContent is TranslatableTextContent && argumentContent.key == ATTACK_DAMAGE_NAME
        }
    }

    /**
     * Sum of the unconditional `minecraft:damage` effects contributed by this mod's enchantments on
     * [stack], evaluated at each enchantment's level. `0f` when there are none.
     */
    private fun enchantmentDamageBonus(stack: ItemStack): Float {
        val enchantments = EnchantmentHelper.getEnchantments(stack)
        if (enchantments.isEmpty) return 0f

        var total = 0f
        for (entry in enchantments.enchantments) {
            val key = entry.key.orElse(null) ?: continue
            if (key.value.namespace != SimpleTweaks.MOD_ID) continue

            val level = enchantments.getLevel(entry)
            if (level <= 0) continue

            val effects = entry.value().effects().get(EnchantmentEffectComponentTypes.DAMAGE) ?: continue
            for (effect in effects) {
                // See restriction 2 in the KDoc: a conditional entry cannot be evaluated here.
                if (effect.requirements().isPresent) continue
                val added = effect.effect().apply(level, RANDOM, 0f)
                total += added
                // Debug-only probe (the `[FST-*]` switch is off by default, so silent in play). It
                // separates the two ways this can fail: no probe line at all means the client's
                // enchantment registry handed back no `minecraft:damage` component — the one assumption
                // this file makes about client/server registry contents — while a probe line with no
                // visible change would be a rendering/identification problem instead.
                FSTLog.log("EnchantDamageTooltip") {
                    "enchantment=${key.value}, level=$level, added=$added, total=$total"
                }
            }
        }
        return total
    }

    /**
     * Formats a value with **vanilla's own** `DecimalFormat`, so the digits (rounding, trailing zeros,
     * locale decimal separator) are exactly what vanilla would have printed for the same number.
     * `DecimalFormat` is not thread-safe, but it is only touched from the tooltip callback, which runs
     * on the render thread — the same thread vanilla itself renders tooltips on.
     */
    private fun formatNumber(value: Float): String = AttributeModifiersComponent.DECIMAL_FORMAT.format(value)
}
