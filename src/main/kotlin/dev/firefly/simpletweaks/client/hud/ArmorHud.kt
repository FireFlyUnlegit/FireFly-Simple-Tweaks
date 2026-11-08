package dev.firefly.simpletweaks.client.hud

import net.minecraft.client.gui.DrawContext
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.MathHelper

/**
 * Armour as **one framed bar**, armoured value only.
 *
 * Armour **toughness** used to be a 1 px cyan line along this bar's bottom edge; since `build=cleanup92` it
 * is its own bar in the right-hand column, above hunger ([ToughnessHud]). The author's reasoning: toughness
 * is a property of the armour set and belongs with the "what keeps me alive" readouts on the right, not
 * tucked under a left-hand bar.
 *
 * ## Why the bar is drawn even at 0 armour
 * An empty bar with `0` is self-explanatory; a bar that silently disappears is indistinguishable from
 * "the feature is broken" — which is exactly what was reported when it was hidden (`build=cleanup91`).
 * The **number** carries the state, not the presence of the widget.
 *
 * ## The 20 cap
 * Armour above 20 keeps reducing damage in vanilla, but a bar that never fills past 20 % of its width for a
 * 100-armour set reads as broken. The bar saturates at 20 and the number carries the truth.
 *
 * Drawn from the health bar's pass, not from a `renderArmor` injection; see [BarHealthHud] for why.
 */
object ArmorHud {

    private val FILL_TOP = 0xFFBFD4E6.toInt()
    private val FILL_BOTTOM = 0xFF5A6E82.toInt()
    private val TEXT = 0xFFFFFFFF.toInt()

    /** Armour value that fills the bar: vanilla's damage-reduction wall. */
    private const val REFERENCE = 20f

    fun render(context: DrawContext, player: PlayerEntity, x: Int, y: Int) {
        // The caller passes the icon column; the bar starts right after it.
        val barX = x + HudBar.ICON
        val armor = player.armor

        HudBar.icon(context, HudBar.ARMOR_EMPTY, HudBar.ARMOR_FULL, x, y)
        HudBar.frame(context, barX, y, HudBar.WIDTH, HudBar.HEIGHT)
        HudBar.fill(
            context, barX, y, HudBar.WIDTH, HudBar.HEIGHT,
            (armor / REFERENCE).coerceIn(0f, 1f), FILL_TOP, FILL_BOTTOM,
        )
        HudBar.text(context, armor.toString(), barX, y, HudBar.WIDTH, TEXT)
    }
}
