package dev.firefly.simpletweaks.client.hud

import net.minecraft.client.gui.DrawContext
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.MathHelper

/**
 * The player's health as **one framed bar**, with absorption as a second gold bar **above** it.
 *
 * ## What this owns
 * Only the drawing. `InGameHudHealthMixin` decides whether to replace vanilla's ten hearts at all (the
 * `/stconfig` toggle); this object draws the replacement.
 *
 * ## The two health arguments of `renderHealthBar`
 * `InGameHud#renderStatusBars` passes `ceil(player.getHealth())` as the parameter Yarn names `lastHealth`
 * and its own **animated** `renderHealthValue` as the one named `health` (bytecode verified, `build=cleanup87`).
 * Reading the latter made the bar trail the number for about a second — the reported "血量不变之后才看到
 * 血条更新". This file only ever receives the real value.
 *
 * ## Absorption lives in the row above (`build=cleanup89`)
 * It used to be a gold strip on the health bar's top edge, which collided with the bar's own highlight and
 * made the number unreadable. It is now a **second bar one row up, drawn exactly like the health bar** in
 * gold — the author's request — with its own absorbing-heart icon and the amount inside it.
 */
object BarHealthHud {

    // Health: red gradient.
    private val FILL_TOP = 0xFFE0524A.toInt()
    private val FILL_BOTTOM = 0xFF8E1B1B.toInt()
    private val GHOST = 0x99FF8A65.toInt()
    private val TEXT = 0xFFFFFFFF.toInt()

    // Absorption: same drawing, gold palette.
    private val ABSORB_TOP = 0xFFFFE082.toInt()
    private val ABSORB_BOTTOM = 0xFFB8860B.toInt()
    private val ABSORB_TEXT = 0xFFFFF3C4.toInt()

    /** Vanilla's damage-flash window. */
    private const val BLINK_TICKS = 20

    /** Ghost catch-up: share of the gap per frame, with a floor so a small chip still moves. */
    private const val GHOST_LERP = 0.2f
    private const val GHOST_MIN_STEP = 0.25f

    /** Frames the ghost is held at the old value after a hit. */
    private const val GHOST_HOLD_FRAMES = 6

    private var ghost = Float.NaN
    private var holdFrames = 0

    /**
     * Draws the health bar at ([x], [y]) and, when the player has absorption, a gold bar one row above.
     *
     * @param health the **real** health (`ceil(player.getHealth())`, vanilla's `lastHealth` argument)
     */
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

        // --- ghost bookkeeping --------------------------------------------------------------------
        if (ghost.isNaN()) ghost = current
        if (current < ghost) {
            if (holdFrames < GHOST_HOLD_FRAMES) {
                holdFrames++
            } else {
                val gap = ghost - current
                ghost = maxOf(ghost - maxOf(gap * GHOST_LERP, GHOST_MIN_STEP), current)
            }
        } else {
            ghost = current
            holdFrames = 0
        }

        // --- icon + health bar ---------------------------------------------------------------------
        val barX = x + HudBar.ICON
        context.drawGuiTexture(HudBar.HEART_FULL, x, y, 9, 9)
        HudBar.frame(context, barX, y, HudBar.WIDTH, HudBar.HEIGHT)

        val innerW = HudBar.WIDTH - 2
        val realRatio = (current / max).coerceIn(0f, 1f)
        val filledPx = MathHelper.ceil(innerW * realRatio).toInt()
        val ghostPx = MathHelper.ceil(innerW * (ghost / max).coerceIn(0f, 1f)).toInt()

        // Ghost slice between the real fill and the trail; drawn first so the fill wins on overlap.
        if (ghostPx > filledPx) {
            context.fill(barX + 1 + filledPx, y + 1, barX + 1 + ghostPx, y + HudBar.HEIGHT - 1, GHOST)
        }

        val flash = blinking && player.hurtTime > BLINK_TICKS / 2
        HudBar.fill(context, barX, y, HudBar.WIDTH, HudBar.HEIGHT, realRatio, FILL_TOP, FILL_BOTTOM, flash)
        HudBar.text(context, "${MathHelper.ceil(current)}/${MathHelper.ceil(max)}", barX, y, HudBar.WIDTH, TEXT)

        // --- absorption: same bar, one row up, gold ------------------------------------------------
        if (absorption > 0) {
            val absorbY = y - 10
            context.drawGuiTexture(HudBar.HEART_ABSORBING, x, absorbY, 9, 9)
            HudBar.frame(context, barX, absorbY, HudBar.WIDTH, HudBar.HEIGHT)
            HudBar.fill(
                context, barX, absorbY, HudBar.WIDTH, HudBar.HEIGHT,
                (absorption / max).coerceIn(0f, 1f), ABSORB_TOP, ABSORB_BOTTOM,
            )
            HudBar.text(
                context, "+${MathHelper.ceil(absorption.toFloat())}",
                barX, absorbY, HudBar.WIDTH, ABSORB_TEXT,
            )
        }
    }

    /** Reset the ghost on world change, so a new life does not inherit the previous trail. */
    fun reset() {
        ghost = Float.NaN
        holdFrames = 0
    }
}
