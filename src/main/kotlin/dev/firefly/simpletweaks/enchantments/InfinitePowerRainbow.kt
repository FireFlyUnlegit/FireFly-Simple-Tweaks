package dev.firefly.simpletweaks.enchantments

import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Formatting

/**
 * 1.12.2 `EnchantInfinitePower.toRainbow` — the animated rainbow name of the `infinite_power`
 * enchantment, which overrode `ModEnchantments.decorateName` instead of using the tier colour.
 *
 * <h2>1.12.2 original (kept verbatim so it can be diffed)</h2>
 * <pre>
 *   private fun toRainbow(text: String): String {
 *       val colors = listOf(RED, GOLD, YELLOW, GREEN, AQUA, BLUE, LIGHT_PURPLE, DARK_PURPLE)
 *       val sb = StringBuilder()
 *       val tick = colorTick / 20f
 *       for (i in text.indices) {
 *           val idx = ((tick + i * 0.08f) * colors.size).toInt() % colors.size
 *           sb.append(colors[idx]).append(text[i])
 *       }
 *       return sb.toString()
 *   }
 *   private var colorTick = 0
 *   fun tick() { colorTick = (colorTick + 1) % 60 }
 * </pre>
 *
 * <h2>What had to change for 1.21, and why the maths is untouched</h2>
 * 1.12.2 built a `String` with legacy `§` codes because `getTranslatedName` returned a `String`.
 * 1.21 names are [Text] components, so the same per-character decision is expressed by giving each
 * character its own [Formatting] style. The index formula, the colour list, the `0.08f` character
 * offset and the 60-tick cycle are **copied exactly** — only the output type changed.
 *
 * <p>Faithfulness note: the original advanced `colorTick` from a client tick hook. The 1.21 port does
 * the same, driven from [dev.firefly.simpletweaks.compat.bridge.ClientEventBridge] `END_CLIENT_TICK`,
 * so the animation speed and its "pauses when the game pauses" behaviour are preserved. A world-time
 * based phase was considered and rejected: it would put every client in phase, which the original did
 * not do.
 */
object InfinitePowerRainbow {

    /** The 1.12.2 `colors` list, in order — the order is what produces the rainbow sequence. */
    private val COLORS = listOf(
        Formatting.RED,
        Formatting.GOLD,
        Formatting.YELLOW,
        Formatting.GREEN,
        Formatting.AQUA,
        Formatting.BLUE,
        Formatting.LIGHT_PURPLE,
        Formatting.DARK_PURPLE,
    )

    private var colorTick = 0

    /** Advance the animation. Called once per client tick. */
    fun tick() {
        colorTick = (colorTick + 1) % 60
    }

    /**
     * Re-colour [original] character by character.
     *
     * Built from `original.getString()`, i.e. the plain text, so any style already on the component is
     * replaced — which is what 1.12.2 did too, since it prefixed `§` codes onto a plain string.
     */
    fun apply(original: Text): Text {
        val text = original.string
        val tick = colorTick / 20f
        val out: MutableText = Text.empty()
        for (i in text.indices) {
            val idx = ((tick + i * 0.08f) * COLORS.size).toInt() % COLORS.size
            out.append(Text.literal(text[i].toString()).formatted(COLORS[idx]))
        }
        // Preserve the outer style (colour is overridden per character, but other attributes such as
        // the italic flag that tooltips apply to enchantment lines must survive).
        return out.setStyle(original.style)
    }
}
