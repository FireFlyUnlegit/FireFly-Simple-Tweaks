package dev.firefly.simpletweaks.client.tooltips

import dev.firefly.simpletweaks.SimpleTweaks
import dev.firefly.simpletweaks.compat.FSTLog
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.minecraft.component.EnchantmentEffectComponentTypes
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.item.ItemStack
import net.minecraft.text.Text
import net.minecraft.text.TranslatableTextContent
import net.minecraft.util.Formatting
import net.minecraft.util.math.random.Random

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
 * 0. **The item must declare attack damage in its own `ATTRIBUTE_MODIFIERS`.** This is what keeps the
 *    fold — and, more importantly, the fallback line — off an **enchanted book**. Books are not an
 *    edge case here: `EnchantmentHelper.getEnchantments` reads `STORED_ENCHANTMENTS` for an enchanted
 *    book (bytecode: `getEnchantmentsComponentType` tests `isOf(Items.ENCHANTED_BOOK)`), so a book's
 *    enchantments *are* visible to this file, while a book has no attack damage and therefore no total
 *    line to correct — every such book therefore took the fallback and grew a bogus `+X 攻击伤害`
 *    line. An item with attack damage but no rendered attribute block (creative search) is unaffected
 *    and still gets the fallback, which is what §X5 asks for.
 * 1. **This mod's namespace only** for the damage sum — another mod's damage effect may be conditional
 *    in ways its `requirements` do not capture.
 * 2. **Entries carrying `requirements` are skipped** (vanilla Sharpness/Smite/Bane are mob-type
 *    conditional; a tooltip has no `LootContext` to evaluate that with).
 * 3. **Position and identification go by translation key, never by rendered string** — the keys
 *    (`attribute.modifier.*`, `item.modifiers.*`) and the attack-damage attribute's own key (read from
 *    the registry: 1.21.1 spells it `attribute.name.generic.attack_damage`) are stable across
 *    languages. `EnchantmentNameColorMixin`'s KDoc records the same reasoning.
 *
 * The sibling-aware line matching and the number rewrite now live in [TooltipTotals], shared with
 * [EnchantAttributeToolTipHandler] — the statements are the ones this file has carried since the
 * sibling bug was found, moved so the two folds cannot drift apart.
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

            // 🔴 Only for an item that has attack damage of its own.
            //
            // `EnchantmentHelper.getEnchantments` returns the **stored** enchantments for an enchanted
            // book (bytecode: `getEnchantmentsComponentType` checks `isOf(Items.ENCHANTED_BOOK)`), so
            // without this gate every enchanted book carrying one of this mod's damage enchantments got
            // a `+X 攻击伤害` line: a book has no attack-damage total line, so the fold failed and the
            // fallback below fired. A book neither deals attack damage nor has anything to correct, so
            // it must be skipped entirely rather than fall back.
            //
            // Note this deliberately does *not* also require the total line to exist: an item whose
            // attribute block is not rendered (creative search) still has attack damage, and that is
            // precisely the case the fallback line exists for (checklist §X5).
            if (!TooltipTotals.declaresAttribute(stack, EntityAttributes.GENERIC_ATTACK_DAMAGE)) return@register

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
        val target = TooltipTotals.findTotalLine(lines, ATTACK_DAMAGE_NAME) ?: return false

        val numberIndex = target.content.args.indexOfFirst {
            TooltipTotals.parseFlatNumber(TooltipTotals.numberOf(it)) != null
        }
        if (numberIndex < 0) return false
        val current = TooltipTotals.parseFlatNumber(TooltipTotals.numberOf(target.content.args[numberIndex]))
            ?: return false

        val merged = TooltipTotals.format(current + bonus)
        if (!TooltipTotals.replaceNumber(lines, target, merged)) return false

        FSTLog.log("EnchantDamageTooltip") {
            "total=${TooltipTotals.format(current)}, enchantmentBonus=$bonus, shown=$merged, " +
                "line=${target.lineIndex}, sibling=${target.siblingIndex}, outcome=folded-into-vanilla-total"
        }
        return true
    }

    /** Vanilla-style additive line, used only when the total cannot be rewritten. */
    private fun bonusLine(bonus: Float): Text =
        Text.translatable(
            "attribute.modifier.plus.0",
            TooltipTotals.format(bonus),
            Text.translatable(ATTACK_DAMAGE_NAME),
        ).formatted(Formatting.DARK_GREEN)

    /**
     * Where the fallback line goes: right after the last node naming the attack-damage attribute, else
     * at the end of the held-item block (before the next `item.modifiers.*` header), else at the very
     * end — in which case it is still better than no number at all.
     *
     * Sibling-aware like [TooltipTotals.findTotalLine]: with the tree walked properly, the item's own
     * total is recognised too, so the fallback lands directly under `7 攻击伤害` rather than only under
     * the enchantment block's `+20%` line.
     */
    private fun insertionIndex(lines: List<Text>): Int {
        var lastAttackDamage = -1
        var firstHeader = -1
        var nextHeader = -1

        lines.forEachIndexed { index, line ->
            for (content in TooltipTotals.nodes(line)) {
                if (content.key.startsWith("item.modifiers.")) {
                    if (firstHeader < 0) firstHeader = index else if (nextHeader < 0) nextHeader = index
                }
                if (TooltipTotals.namesAttribute(content, ATTACK_DAMAGE_NAME)) {
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
}
