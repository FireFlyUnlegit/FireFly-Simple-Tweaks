package dev.firefly.simpletweaks.client.hud

import net.minecraft.client.gui.DrawContext
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.MathHelper

/**
 * Armour **toughness** as its own bar, on the right-hand column **above hunger** (`build=cleanup92`).
 *
 * ## Why it is no longer a line on the armour bar
 * The first version drew toughness as a 1 px cyan line along the bottom of the armour bar. The author's
 * call: toughness belongs with the **survival column** on the right (above hunger), not tucked under a
 * left-hand bar — it is a property of the armour set the player is wearing, and the right column is where
 * the "what keeps me alive" readouts already live.
 *
 * ## Icon
 * The vanilla sprite set has no toughness glyph (vanilla draws it as small pips above the armour icons),
 * so this reuses `hud/armor_full` in a cyan palette: same silhouette as the armour bar, different colour,
 * which is exactly the relationship the two stats have.
 *
 * ## Visibility
 * Drawn only when toughness is above zero. Unlike the armour bar — where a missing widget was
 * indistinguishable from a broken feature — a 0-toughness bar would be a permanent empty row next to
 * hunger explaining nothing. The case only becomes reachable at all with modded armour.
 *
 * ## Scale
 * `min(toughness, 20) / 20`, matching the armour bar's reference so the two columns read on the same scale.
 */
object ToughnessHud {

    private val FILL_TOP = 0xFF9EE8F0.toInt()
    private val FILL_BOTTOM = 0xFF3E7E8C.toInt()
    private val TEXT = 0xFFFFFFFF.toInt()

    /** Same reference as [ArmorHud]: 20 is "a full bar" for both. */
    private const val REFERENCE = 20f

    /** Right edge of the bar is [rightEdge]; the icon hangs outside it, mirroring hunger. */
    fun renderRightAligned(context: DrawContext, player: PlayerEntity, rightEdge: Int, y: Int) {
        val toughness = player.getAttributeValue(
            net.minecraft.entity.attribute.EntityAttributes.GENERIC_ARMOR_TOUGHNESS
        )
        if (toughness <= 0.0) return

        val barX = rightEdge - HudBar.WIDTH
        HudBar.frame(context, barX, y, HudBar.WIDTH, HudBar.HEIGHT)
        HudBar.fill(
            context, barX, y, HudBar.WIDTH, HudBar.HEIGHT,
            (toughness / REFERENCE).coerceIn(0.0, 1.0).toFloat(), FILL_TOP, FILL_BOTTOM,
        )
        // `12` rather than `12.0` for the common case; one decimal when a mod gives a fractional value.
        val shown = if (toughness == Math.floor(toughness)) {
            MathHelper.ceil(toughness).toString()
        } else {
            "%.1f".format(toughness)
        }
        HudBar.text(context, shown, barX, y, HudBar.WIDTH, TEXT)
        HudBar.icon(context, HudBar.ARMOR_EMPTY, HudBar.ARMOR_FULL, rightEdge, y)
    }
}
