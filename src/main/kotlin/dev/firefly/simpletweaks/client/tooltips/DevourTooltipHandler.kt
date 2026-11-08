package dev.firefly.simpletweaks.client.tooltips

import dev.firefly.simpletweaks.enchantments.handlers.mythic.EnchantDevourHandler
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.minecraft.text.Text
import net.minecraft.util.Formatting

/**
 * Shows 吞噬's banked stacks on the weapon's tooltip.
 *
 * ## Why this is not optional polish
 * `devour` stores its progression in the item's `CUSTOM_DATA` (`build=cleanup77`), which the player
 * otherwise cannot see anywhere: without this line the tooltip of a fully fed weapon is **byte-identical**
 * to a fresh one, so "the hits feel a bit harder" is the only feedback. That is exactly the
 * "no observation = no way to tell 'did not trigger' from 'not implemented'" problem `AGENTS.md` §4.4
 * describes, applied to a persistent counter.
 *
 * The line is added only when the stack carries the enchantment **and** has at least one stack, so a
 * freshly enchanted weapon stays clean.
 */
object DevourTooltipHandler {

    private const val LINE_KEY = "gui.simple_tweaks.devour.stacks"

    fun register() {
        ItemTooltipCallback.EVENT.register { stack, _, _, lines ->
            if (stack.isEmpty) return@register
            val stacks = EnchantDevourHandler.stacksOf(stack)
            if (stacks <= 0) return@register
            // The percentage is formatted to a string on purpose: passing the `Float` straight into
            // `translatable` renders `Float.toString`, i.e. `30.0` (and `24.000002` for counts where the
            // float maths does not land exactly) instead of `30`. Numbers in a tooltip are text.
            val percent = EnchantDevourHandler.BONUS_PER_STACK * 100f * stacks
            lines.add(
                Text.translatable(LINE_KEY, stacks, formatPercent(percent))
                    .formatted(Formatting.DARK_PURPLE),
            )
        }
    }

    /** `2`, `30`, `7.5` — never `30.0` / `24.000002`. */
    private fun formatPercent(value: Float): String =
        "%.1f".format(value).removeSuffix(".0")
}
