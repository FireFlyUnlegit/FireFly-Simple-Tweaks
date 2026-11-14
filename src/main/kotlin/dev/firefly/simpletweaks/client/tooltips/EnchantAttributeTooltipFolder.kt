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
 * Rewrites the enchantment-granted armour/health/toughness lines into plain totals, the way vanilla
 * prints a weapon's `4 攻击伤害`: one `attribute.modifier.equals.0` line per attribute instead of one
 * `+0.3 护甲值` line per enchantment (a fully enchanted piece prints 30+ of them).
 *
 * Percent lines are left alone.
 */
object EnchantAttributeTooltipFolder {

    private const val PLUS_FLAT = "attribute.modifier.plus.0"
    private const val TAKE_FLAT = "attribute.modifier.take.0"

    /** Attributes the rarity ladder's armour package touches. */
    private val TOTAL_ATTRIBUTES = setOf(
        "attribute.name.generic.max_health",
        "attribute.name.generic.armor",
        "attribute.name.generic.armor_toughness",
    )

    private class FlatLine(val index: Int, val value: Double, val owned: Boolean)

    private class Owned(val attributeKey: String, val operation: Int, val value: Double)

    fun register() {
        ItemTooltipCallback.EVENT.register { stack, _, _, lines ->
            if (stack.isEmpty) return@register
            if (lines.isEmpty()) return@register

            val enchantmentAttributes = ourAttributeKeys(stack)
            if (enchantmentAttributes.isEmpty()) return@register

            for (attributeKey in enchantmentAttributes) {
                if (attributeKey in TOTAL_ATTRIBUTES) fold(stack, lines, attributeKey)
            }
        }
    }

    private fun fold(stack: ItemStack, lines: MutableList<Text>, attributeKey: String) {
        // An item that already prints a flat total (a sword's attack damage) keeps vanilla's number: the
        // other handler folds enchantment bonuses into it.
        if (TooltipTotals.findTotalLine(lines, attributeKey) != null) return

        val owned = ownedModifiers(stack)
        val flats = mutableListOf<FlatLine>()

        lines.forEachIndexed { index, line ->
            for (node in TooltipTotals.nodes(line)) {
                if (node.key != PLUS_FLAT && node.key != TAKE_FLAT) continue
                if (attributeKeyOf(node) != attributeKey) continue

                val raw = node.args.firstOrNull() ?: continue
                val value = TooltipTotals.parseFlatNumber(TooltipTotals.numberOf(raw)) ?: continue
                val signed = if (node.key == TAKE_FLAT) -value.toDouble() else value.toDouble()
                flats += FlatLine(index, signed, owned.consume(Owned(attributeKey, 0, signed)))
                break
            }
        }

        // Only the item's own modifier(s): nothing was granted by an enchantment here.
        if (flats.none { !it.owned }) return

        val total = flats.sumOf { it.value }
        if (kotlin.math.abs(total) < 0.001) return

        val insertAt = flats.minOf { it.index }
        for (flat in flats.sortedByDescending { it.index }) lines.removeAt(flat.index)
        lines.add(
            insertAt,
            Text.translatable(
                TooltipTotals.TOTAL_KEY,
                TooltipTotals.format(kotlin.math.abs(total).toFloat()),
                Text.translatable(attributeKey),
            ).formatted(Formatting.DARK_GREEN),
        )

        FSTLog.log("AttributeTooltip") {
            val detail = flats.joinToString("+") { it.value.toString() }
            "stack=${stack.item.translationKey}, attribute=${attributeKey.substringAfterLast('.')}, " +
                "lines=[$detail], shown=${TooltipTotals.format(kotlin.math.abs(total).toFloat())}, outcome=total"
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
                kotlin.math.abs(it.value - candidate.value) < 0.001
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
