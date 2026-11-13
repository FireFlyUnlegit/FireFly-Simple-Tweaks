package dev.firefly.simpletweaks.client.hud

import net.minecraft.client.gui.DrawContext
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.MathHelper

/** Armour toughness bar, right column above hunger. Hidden at 0 — an empty row there explains nothing. */
object ToughnessHud {

    private val FILL_TOP = 0xFF9EE8F0.toInt()
    private val FILL_BOTTOM = 0xFF3E7E8C.toInt()
    private val TEXT = 0xFFFFFFFF.toInt()

    private const val REFERENCE = 20f

    private val smooth = HudBar.Smooth()

    fun renderRightAligned(context: DrawContext, player: PlayerEntity, rightEdge: Int, y: Int) {
        val toughness = player.getAttributeValue(
            net.minecraft.entity.attribute.EntityAttributes.GENERIC_ARMOR_TOUGHNESS
        )
        if (toughness <= 0.0) {
            smooth.reset()
            return
        }
        val shown = smooth.update(toughness.toFloat())

        val barX = rightEdge - HudBar.WIDTH
        HudBar.frame(context, barX, y, HudBar.WIDTH, HudBar.HEIGHT)
        HudBar.fill(
            context, barX, y, HudBar.WIDTH, HudBar.HEIGHT,
            (shown / REFERENCE).coerceIn(0f, 1f), FILL_TOP, FILL_BOTTOM,
        )
        val value = if (toughness == Math.floor(toughness)) {
            MathHelper.ceil(toughness).toString()
        } else {
            "%.1f".format(toughness)
        }
        HudBar.text(context, value, barX, y, HudBar.WIDTH, TEXT)
        // No vanilla toughness sprite; the armour glyph in a different palette.
        HudBar.icon(context, HudBar.ARMOR_EMPTY, HudBar.ARMOR_FULL, rightEdge, y)
    }

    fun reset() {
        smooth.reset()
    }
}
