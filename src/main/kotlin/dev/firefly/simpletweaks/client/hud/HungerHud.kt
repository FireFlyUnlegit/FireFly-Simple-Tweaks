package dev.firefly.simpletweaks.client.hud

import net.minecraft.client.gui.DrawContext
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.MathHelper

/**
 * Hunger as **one framed bar**, mirrored to the right, with its icon on the **right** of the bar.
 *
 * ## Why the icon is on the right (`build=cleanup90`)
 * The block is anchored to vanilla's right edge (`width / 2 + 91`), where the food icons have always been.
 * With the icon on the left the widget pointed *inwards*, away from the screen edge the player's eye uses
 * to find hunger; mirrored — `[bar] 🍗` — it reads as a continuation of the right-hand HUD column, the same
 * way the health block reads as a continuation of the left one.
 *
 * ## The number
 * `foodLevel` (0..20) is the only value that gates sprinting and regeneration; saturation is an internal
 * buffer the player cannot act on, so it stays hidden. The bar shows `foodLevel / 20` and prints `foodLevel`.
 *
 * Drawn from the health bar's pass, not from a `renderFood` injection; see [BarHealthHud] for why.
 */
object HungerHud {

    private val FILL_TOP = 0xFFE8A33D.toInt()
    private val FILL_BOTTOM = 0xFF8A4A12.toInt()
    private val TEXT = 0xFFFFFFFF.toInt()

    /** Vanilla's hunger scale: `foodLevel` is 0..20. */
    private const val REFERENCE = 20f

    /**
     * Draws the block so its **right** edge is at [rightEdge]: `[bar][icon]`.
     *
     * @param rightEdge `width / 2 + 91`, i.e. where vanilla's food row ends
     */
    fun renderRightAligned(context: DrawContext, player: PlayerEntity, rightEdge: Int, y: Int) {
        val food = player.hungerManager.foodLevel
        // The bar's own right edge is the anchor; the icon hangs outside it (author, cleanup91).
        val barX = rightEdge - HudBar.WIDTH
        HudBar.frame(context, barX, y, HudBar.WIDTH, HudBar.HEIGHT)
        HudBar.fill(
            context, barX, y, HudBar.WIDTH, HudBar.HEIGHT,
            (food / REFERENCE).coerceIn(0f, 1f), FILL_TOP, FILL_BOTTOM,
        )
        HudBar.text(context, MathHelper.ceil(food.toFloat()).toString(), barX, y, HudBar.WIDTH, TEXT)
        HudBar.icon(context, HudBar.FOOD_EMPTY, HudBar.FOOD_FULL, rightEdge, y)
    }
}
