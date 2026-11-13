package dev.firefly.simpletweaks.client.hud

import net.minecraft.client.gui.DrawContext
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.MathHelper

/** Hunger bar, mirrored to vanilla's right edge with the icon on the right of the bar. */
object HungerHud {

    private val FILL_TOP = 0xFFE8A33D.toInt()
    private val FILL_BOTTOM = 0xFF8A4A12.toInt()
    private val TEXT = 0xFFFFFFFF.toInt()

    private const val REFERENCE = 20f

    private val smooth = HudBar.Smooth()

    fun renderRightAligned(context: DrawContext, player: PlayerEntity, rightEdge: Int, y: Int) {
        val food = player.hungerManager.foodLevel
        val shown = smooth.update(food.toFloat())

        val barX = rightEdge - HudBar.WIDTH
        HudBar.frame(context, barX, y, HudBar.WIDTH, HudBar.HEIGHT)
        HudBar.fill(
            context, barX, y, HudBar.WIDTH, HudBar.HEIGHT,
            (shown / REFERENCE).coerceIn(0f, 1f), FILL_TOP, FILL_BOTTOM,
        )
        HudBar.text(context, MathHelper.ceil(food.toFloat()).toString(), barX, y, HudBar.WIDTH, TEXT)
        HudBar.icon(context, HudBar.FOOD_EMPTY, HudBar.FOOD_FULL, rightEdge, y)
    }

    fun reset() {
        smooth.reset()
    }
}
