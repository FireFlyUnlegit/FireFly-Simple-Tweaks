package dev.firefly.simpletweaks.client.tooltips

import dev.firefly.simpletweaks.SimpleTweaks
import dev.firefly.simpletweaks.compat.FSTLog
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.minecraft.client.gui.screen.Screen
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.EnchantmentEffectComponentTypes
import net.minecraft.component.type.AttributeModifiersComponent
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.item.ItemStack
import net.minecraft.text.Text
import net.minecraft.text.TranslatableTextContent
import net.minecraft.util.Formatting

/**
 * Sums this mod's enchantment-granted attribute lines into one short block, per attribute.
 *
 * Something on the client mirrors every enchantment `minecraft:attributes` effect into the stack's
 * tooltip, one line each. On a fully enchanted armour piece the derived packages (max health / armour /
 * toughness on every armour enchantment) turn into 30+ lines that bury the item's own stats. Vanilla
 * does not draw them, so this is a post-process on the finished line list.
 *
 * Matching goes by translation **key** (`attribute.modifier.plus|take.<op>` plus the attribute's own
 * `attribute.name.*`), never by rendered text, so it survives language changes.
 *
 * A line is only folded when it is **not** one of the item's own modifiers: the stack's
 * `ATTRIBUTE_MODIFIERS` component is consumed entry by entry first, which is what keeps
 * `+4 护甲值` (the item) apart from `+0.3 护甲值` (an enchantment).
 *
 * Shift expands, same convention as vanilla's own "hold shift" hints.
 */
object EnchantAttributeTooltipFolder {

    /** Header of the folded block. */
    private const val FOLDED_KEY = "gui.simple_tweaks.attributes.folded"

    /** Below this many lines there is nothing worth collapsing. */
    private const val MIN_MATCHES = 4

    private const val VALUE_TOLERANCE = 0.001

    private class Candidate(
        val lineIndex: Int,
        val attributeKey: String,
        val operation: Int,
        val value: Double,
    )

    private class Owned(val attributeKey: String, val operation: Int, val value: Double)

    fun register() {
        ItemTooltipCallback.EVENT.register { stack, _, _, lines ->
            if (stack.isEmpty) return@register

            val attributeKeys = ourAttributeKeys(stack)
            if (attributeKeys.isEmpty()) return@register

            val owned = ownedModifiers(stack)
            val candidates = mutableListOf<Candidate>()

            lines.forEachIndexed { index, line ->
                // One candidate per line: a line is removed by index, so two candidates on the same
                // index would delete the wrong line on the second removal.
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

            if (candidates.isEmpty()) return@register

            if (Screen.hasShiftDown()) {
                FSTLog.log("AttributeTooltip") {
                    "candidates=${candidates.size}, outcome=expanded"
                }
                return@register
            }
            if (candidates.size < MIN_MATCHES) {
                FSTLog.log("AttributeTooltip") {
                    "candidates=${candidates.size}, outcome=too-few-to-fold"
                }
                return@register
            }

            val sums = LinkedHashMap<Pair<String, Int>, Double>()
            for (candidate in candidates) {
                sums.merge(candidate.attributeKey to candidate.operation, candidate.value, Double::plus)
            }

            val insertAt = candidates.first().lineIndex
            for (candidate in candidates.asReversed()) lines.removeAt(candidate.lineIndex)

            val block = mutableListOf<Text>()
            block += Text.translatable(FOLDED_KEY).formatted(Formatting.AQUA)
            for ((key, sum) in sums) {
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
                val detail = sums.entries.joinToString(", ") { "${it.key.first.substringAfterLast('.')}=${it.value}" }
                "folded=${candidates.size}, attributes=${sums.size}, totals=[$detail], outcome=folded"
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
