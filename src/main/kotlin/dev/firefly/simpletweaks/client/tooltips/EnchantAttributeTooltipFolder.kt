package dev.firefly.simpletweaks.client.tooltips

import dev.firefly.simpletweaks.SimpleTweaks
import dev.firefly.simpletweaks.compat.FSTLog
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.EnchantmentEffectComponentTypes
import net.minecraft.component.type.AttributeModifiersComponent
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.item.ItemStack
import net.minecraft.text.Text
import net.minecraft.text.TranslatableTextContent
import net.minecraft.util.Formatting

/**
 * Sums this mod's enchantment-granted attribute lines: one line per attribute, plus a single line for
 * `prismatic_blessing`, whose 17 attributes all carry the same percentage.
 *
 * Whatever mirrors enchantment `minecraft:attributes` into a stack's tooltip prints one line per effect,
 * so a fully enchanted armour piece buries its own stats under 30+ lines. Vanilla does not draw them,
 * which is why this is a post-process on the finished line list.
 *
 * Lines are matched by translation key (`attribute.modifier.plus|take.<op>` plus the `attribute.name.*`
 * argument), never by rendered text. The stack's own `ATTRIBUTE_MODIFIERS` entries are consumed entry by
 * entry first, so an item's `+4 armour` is never folded into an enchantment's `+0.3 armour`.
 */
object EnchantAttributeTooltipFolder {

    /** Header of the folded block. */
    private const val HEADER_KEY = "gui.simple_tweaks.attributes.folded"

    /** `%1$s` = the shared percentage, `%2$s` = how many attributes it covers. */
    private const val PRISMATIC_KEY = "gui.simple_tweaks.prismatic.folded"

    private const val PRISMATIC_ID = "prismatic_blessing"

    /** Below this many lines there is nothing worth collapsing. */
    private const val MIN_MATCHES = 4

    /** A shared percentage needs at least this many attributes to be worth its own line. */
    private const val MIN_SHARED = 3

    private const val VALUE_TOLERANCE = 0.001

    private class Candidate(
        val lineIndex: Int,
        val attributeKey: String,
        val operation: Int,
        val value: Double,
    )

    private class Owned(val attributeKey: String, val operation: Int, val value: Double)

    /** `prismatic_blessing` on this stack: the attributes it covers and the percentage of each. */
    private class Prismatic(val attributeKeys: Set<String>, val percent: Float, val level: Int)

    fun register() {
        ItemTooltipCallback.EVENT.register { stack, _, _, lines ->
            if (stack.isEmpty) return@register

            // Re-entrancy guard: a rebuilt tooltip can still carry a previous pass's block, and folding
            // our own sums again would add our numbers back into themselves.
            if (lines.any { it.content is TranslatableTextContent && (it.content as TranslatableTextContent).key == HEADER_KEY }) {
                return@register
            }

            val attributeKeys = ourAttributeKeys(stack)
            if (attributeKeys.isEmpty()) return@register

            val owned = ownedModifiers(stack)
            val candidates = mutableListOf<Candidate>()

            lines.forEachIndexed { index, line ->
                // One candidate per line: lines are removed by index, so two candidates on one index
                // would delete the wrong line on the second removal.
                for (node in TooltipTotals.nodes(line)) {
                    if (!node.key.startsWith("attribute.modifier.")) continue
                    if (node.key == TooltipTotals.TOTAL_KEY) continue

                    val attributeKey = attributeKeyOf(node) ?: continue
                    if (attributeKey !in attributeKeys) continue

                    val operation = node.key.substringAfterLast('.').toIntOrNull() ?: continue
                    val raw = node.args.firstOrNull() ?: continue
                    val value = TooltipTotals.parseFlatNumber(TooltipTotals.numberOf(raw)) ?: continue
                    val signed = if (node.key.contains(".take.")) -value.toDouble() else value.toDouble()

                    if (owned.consume(Owned(attributeKey, operation, signed))) break
                    candidates += Candidate(index, attributeKey, operation, signed)
                    break
                }
            }

            val prismatic = prismaticOn(stack)
            val prismaticLines = if (prismatic == null) {
                emptyList()
            } else {
                candidates.filter {
                    it.attributeKey in prismatic.attributeKeys &&
                        kotlin.math.abs(it.value - prismatic.percent) < VALUE_TOLERANCE
                }
            }
            val shared = prismaticLines.size >= MIN_SHARED
            val summed = if (shared) candidates - prismaticLines.toSet() else candidates

            if (!shared && summed.size < MIN_MATCHES) {
                if (candidates.isNotEmpty()) {
                    FSTLog.log("AttributeTooltip") {
                        "candidates=${candidates.size}, outcome=too-few-to-fold"
                    }
                }
                return@register
            }

            val perAttribute = LinkedHashMap<Pair<String, Int>, Double>()
            for (candidate in summed) {
                perAttribute.merge(candidate.attributeKey to candidate.operation, candidate.value, Double::plus)
            }

            val removed = if (shared) candidates else summed
            val insertAt = removed.minOf { it.lineIndex }
            for (candidate in removed.sortedByDescending { it.lineIndex }) lines.removeAt(candidate.lineIndex)

            val block = mutableListOf<Text>()
            block += Text.translatable(HEADER_KEY).formatted(Formatting.AQUA)
            if (shared) {
                block += Text.translatable(
                    PRISMATIC_KEY,
                    TooltipTotals.format(kotlin.math.abs(prismatic!!.percent) * 100f),
                    prismaticLines.size.toString(),
                ).formatted(Formatting.LIGHT_PURPLE)
            }
            for ((key, sum) in perAttribute) {
                val (attributeKey, operation) = key
                val verb = if (sum < 0.0) "take" else "plus"
                block += Text.translatable(
                    "attribute.modifier.$verb.$operation",
                    TooltipTotals.format(kotlin.math.abs(sum).toFloat()),
                    Text.translatable(attributeKey),
                ).formatted(Formatting.BLUE)
            }
            lines.addAll(insertAt, block)

            FSTLog.log("AttributeTooltip") {
                val detail = perAttribute.entries.joinToString(", ") {
                    "${it.key.first.substringAfterLast('.')}=${it.value}"
                }
                val raw = candidates.groupBy { it.attributeKey.substringAfterLast('.') }
                    .entries
                    .joinToString(", ") { (attribute, list) ->
                        "$attribute=${list.joinToString("+") { it.value.toString() }}"
                    }
                    .take(600)
                "stack=${stack.item.translationKey}, folded=${removed.size}, attributes=${perAttribute.size}, " +
                    "prismatic=${if (shared) "${prismaticLines.size}x${prismatic?.percent}" else "none"}, " +
                    "totals=[$detail], raw=[$raw], outcome=folded"
            }
        }
    }

    /** Translation keys of every attribute this mod's enchantments on [stack] add. */
    private fun ourAttributeKeys(stack: ItemStack): Set<String> {
        val enchantments = EnchantmentHelper.getEnchantments(stack)
        if (enchantments.isEmpty) return emptySet()

        val keys = mutableSetOf<String>()
        for (entry in enchantments.enchantments) {
            val key = entry.key.orElse(null) ?: continue
            if (key.value.namespace != SimpleTweaks.MOD_ID) continue

            val effects = entry.value().effects().get(EnchantmentEffectComponentTypes.ATTRIBUTES) ?: continue
            for (effect in effects) keys += effect.attribute().value().translationKey
        }
        return keys
    }

    private fun prismaticOn(stack: ItemStack): Prismatic? {
        val enchantments = EnchantmentHelper.getEnchantments(stack)
        for (entry in enchantments.enchantments) {
            val key = entry.key.orElse(null) ?: continue
            if (key.value.namespace != SimpleTweaks.MOD_ID || key.value.path != PRISMATIC_ID) continue

            val level = enchantments.getLevel(entry)
            if (level <= 0) continue

            val effects = entry.value().effects().get(EnchantmentEffectComponentTypes.ATTRIBUTES) ?: continue
            val first = effects.firstOrNull() ?: continue
            return Prismatic(
                effects.mapTo(mutableSetOf()) { it.attribute().value().translationKey },
                first.amount().getValue(level),
                level,
            )
        }
        return null
    }

    /** The stack's own attribute modifiers, as a consume-as-you-match list. */
    private fun ownedModifiers(stack: ItemStack): MutableList<Owned> {
        val component = stack.getOrDefault(
            DataComponentTypes.ATTRIBUTE_MODIFIERS,
            AttributeModifiersComponent.DEFAULT,
        )
        return component.modifiers().mapTo(mutableListOf()) { entry ->
            Owned(
                entry.attribute().value().translationKey,
                entry.modifier().operation().id,
                entry.modifier().value(),
            )
        }
    }

    private fun MutableList<Owned>.consume(candidate: Owned): Boolean {
        val index = indexOfFirst {
            it.attributeKey == candidate.attributeKey &&
                it.operation == candidate.operation &&
                kotlin.math.abs(it.value - candidate.value) < VALUE_TOLERANCE
        }
        if (index < 0) return false
        removeAt(index)
        return true
    }

    /** The `attribute.name.*` key named by an `attribute.modifier.*` node's argument, if any. */
    private fun attributeKeyOf(node: TranslatableTextContent): String? {
        for (argument in node.args) {
            val content = (argument as? Text)?.content
            if (content is TranslatableTextContent && content.key.startsWith("attribute.name.")) {
                return content.key
            }
        }
        return null
    }
}
