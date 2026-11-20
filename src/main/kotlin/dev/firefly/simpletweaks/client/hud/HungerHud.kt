package dev.firefly.simpletweaks.client.hud

import net.minecraft.client.gui.DrawContext
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.MathHelper

/**
 * Hunger bar, mirrored to vanilla's right edge with the icon on the right of the bar.
 *
 * The number is `food (saturation)`. Saturation has no bar of its own: the part of the fill that is still
 * backed by saturation is painted in a more golden gradient, so "20 (6)" draws an orange bar whose left
 * third is gold. Nothing about the frame changes and no pixel moves — only the colour of the fill.
 */
object HungerHud {

    private val FILL_TOP = 0xFFE8A33D.toInt()
    private val FILL_BOTTOM = 0xFF8A4A12.toInt()
    private val SATURATION_TOP = 0xFFFFE066.toInt()
    private val SATURATION_BOTTOM = 0xFFC79000.toInt()
    private val TEXT = 0xFFFFFFFF.toInt()

    private const val REFERENCE = 20f

    private val smooth = HudBar.Smooth()

    fun renderRightAligned(context: DrawContext, player: PlayerEntity, rightEdge: Int, y: Int) {
        val food = player.hungerManager.foodLevel
        val saturation = player.hungerManager.saturationLevel
        val shown = smooth.update(food.toFloat())

        val barX = rightEdge - HudBar.WIDTH
        HudBar.frame(context, barX, y, HudBar.WIDTH, HudBar.HEIGHT)
        HudBar.fill(
            context, barX, y, HudBar.WIDTH, HudBar.HEIGHT,
            (shown / REFERENCE).coerceIn(0f, 1f), FILL_TOP, FILL_BOTTOM,
        )

        // Repaint the saturated share of the fill in gold. Same geometry, same highlight, so this cannot
        // change the bar's size or the smoothing — only its colour. Saturation never exceeds the food level
        // in vanilla, but the guard keeps a modded hunger manager from painting past the end.
        val inner = HudBar.WIDTH - 2
        val filled = MathHelper.ceil(inner * (shown / REFERENCE).coerceIn(0f, 1f))
        val share = if (food <= 0) 0f else (saturation / food).coerceIn(0f, 1f)
        val golden = MathHelper.ceil(filled * share).coerceIn(0, filled)
        if (golden > 0) {
            HudBar.fill(
                context, barX, y, HudBar.WIDTH, HudBar.HEIGHT,
                (golden.toFloat() / inner).coerceIn(0f, 1f), SATURATION_TOP, SATURATION_BOTTOM,
            )
        }

        HudBar.text(
            context,
            HudBar.number(food.toDouble()) + " (" + HudBar.number(saturation.toDouble()) + ")",
            barX, y, HudBar.WIDTH, TEXT,
        )
        HudBar.icon(context, HudBar.FOOD_EMPTY, HudBar.FOOD_FULL, rightEdge, y)
    }

    fun reset() {
        smooth.reset()
    }
}
