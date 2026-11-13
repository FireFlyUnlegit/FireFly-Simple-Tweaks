package dev.firefly.simpletweaks.client.hud

import net.minecraft.client.gui.DrawContext
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.MathHelper

/** Health bar, plus absorption as a gold bar one row above; fills tween, numbers are exact. */
object BarHealthHud {

    private val FILL_TOP = 0xFFE0524A.toInt()
    private val FILL_BOTTOM = 0xFF8E1B1B.toInt()
    private val GHOST = 0x99FF8A65.toInt()
    private val TEXT = 0xFFFFFFFF.toInt()

    private val ABSORB_TOP = 0xFFFFE082.toInt()
    private val ABSORB_BOTTOM = 0xFFB8860B.toInt()
    private val ABSORB_TEXT = 0xFFFFF3C4.toInt()

    private const val GHOST_LERP = 0.2f
    private const val GHOST_MIN_STEP = 0.25f
    private const val GHOST_HOLD_FRAMES = 6

    private val healthSmooth = HudBar.Smooth()
    private val absorbSmooth = HudBar.Smooth()
    private var ghost = Float.NaN
    private var holdFrames = 0

    fun render(
        context: DrawContext,
        player: PlayerEntity,
        x: Int,
        y: Int,
        maxHealth: Float,
        health: Int,
        absorption: Int,
        blinking: Boolean,
    ) {
        val max = maxHealth.coerceAtLeast(1f)
        val current = health.toFloat().coerceIn(0f, max)

        // Damage ghost: trails behind the (already tweened) fill for the "lost health" read.
        if (ghost.isNaN()) ghost = current
        if (current < ghost) {
            if (holdFrames < GHOST_HOLD_FRAMES) {
                holdFrames++
            } else {
                ghost = maxOf(ghost - maxOf((ghost - current) * GHOST_LERP, GHOST_MIN_STEP), current)
            }
        } else {
            ghost = current
            holdFrames = 0
        }

        val shown = healthSmooth.update(current)

        // Bar edge sits on the HUD anchor; the icon hangs outside it.
        val barX = x
        val iconX = x - HudBar.ICON

        ArmorHud.render(context, player, iconX, y - (if (absorption > 0) 20 else 10))

        HudBar.icon(context, HudBar.HEART_CONTAINER, HudBar.HEART_FULL, iconX, y)
        HudBar.frame(context, barX, y, HudBar.WIDTH, HudBar.HEIGHT)

        val innerW = HudBar.WIDTH - 2
        val filledPx = MathHelper.ceil(innerW * (shown / max).coerceIn(0f, 1f)).toInt()
        val ghostPx = MathHelper.ceil(innerW * (ghost / max).coerceIn(0f, 1f)).toInt()

        if (ghostPx > filledPx) {
            context.fill(barX + 1 + filledPx, y + 1, barX + 1 + ghostPx, y + HudBar.HEIGHT - 1, GHOST)
        }
        HudBar.fill(context, barX, y, HudBar.WIDTH, HudBar.HEIGHT, shown / max, FILL_TOP, FILL_BOTTOM, blinking)
        HudBar.text(context, "${MathHelper.ceil(current)}/${MathHelper.ceil(max)}", barX, y, HudBar.WIDTH, TEXT)

        if (absorption > 0) {
            val absorbY = y - 10
            val absorbShown = absorbSmooth.update(absorption.toFloat())
            HudBar.icon(context, HudBar.HEART_CONTAINER, HudBar.HEART_ABSORBING, iconX, absorbY)
            HudBar.frame(context, barX, absorbY, HudBar.WIDTH, HudBar.HEIGHT)
            HudBar.fill(
                context, barX, absorbY, HudBar.WIDTH, HudBar.HEIGHT,
                (absorbShown / max).coerceIn(0f, 1f), ABSORB_TOP, ABSORB_BOTTOM,
            )
            HudBar.text(
                context, "+${MathHelper.ceil(absorption.toFloat())}",
                barX, absorbY, HudBar.WIDTH, ABSORB_TEXT,
            )
        } else {
            absorbSmooth.reset()
        }

        // Right column: toughness above hunger, both mirrored.
        val rightEdge = context.getScaledWindowWidth() / 2 + 91
        ToughnessHud.renderRightAligned(context, player, rightEdge, y - 10)
        HungerHud.renderRightAligned(context, player, rightEdge, y)
    }

    fun reset() {
        healthSmooth.reset()
        absorbSmooth.reset()
        ghost = Float.NaN
        holdFrames = 0
        ArmorHud.reset()
        ToughnessHud.reset()
        HungerHud.reset()
    }
}
