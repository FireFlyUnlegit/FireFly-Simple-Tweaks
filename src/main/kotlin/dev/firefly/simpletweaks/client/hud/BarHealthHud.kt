package dev.firefly.simpletweaks.client.hud

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.Identifier
import net.minecraft.util.math.MathHelper

/**
 * Replaces vanilla's row of ten hearts with **one framed bar**: `♥ [████ 19/20 ████]`.
 *
 * ## Why replace instead of appending
 * Vanilla draws ten 8×9 heart sprites per row, so a modded player with 40+ max health needs three rows. A
 * bar carries the same information in one row and stays readable at any scale. The mixin that calls this
 * **cancels** `InGameHud#renderHealthBar`; nothing else in the HUD is touched (armour, hunger, air, XP and
 * mount health keep their vanilla renderers). [dev.firefly.simpletweaks.core.config.GeneralConfig.barHealthHud]
 * turns it off, in which case the mixin does not cancel and vanilla hearts are drawn untouched.
 *
 * ## The two health arguments, and why the first one is "real"
 * `InGameHud#renderStatusBars` passes (bytecode, 1.21.1):
 *
 * ```
 * iload 8  → x        iload 10 → y          iload 14 → lines     iload 16 → regeneratingHeartIndex
 * fload 11 → maxHealth
 * iload_3  → ceil(player.getHealth())      ← **real health**
 * iload 7  → this.renderHealthValue        ← **animated display value** (~1 s lerp)
 * iload 12 → absorption                     iload 4  → blinking
 * ```
 *
 * The parameter that *looks* like "health" (the second int) is vanilla's animated `renderHealthValue`;
 * reading it made the bar trail the number for about a second — the reported "血量不变之后才看到血条更新"
 * (`build=cleanup87`). The real value is the parameter vanilla calls `lastHealth`, and this file uses only
 * that one.
 *
 * ## Layout (GUI pixels)
 * ```
 * x  x+9                x+89
 * ♥  [████  19/20  ████]
 * ```
 * The **icon is on the left**, the bar starts immediately after it (no gap), and the value sits **inside**
 * the bar, centred; absorption is a gold top edge plus `+N` right-aligned inside. The block is 89 px wide,
 * inside the 91 px vanilla reserves for the heart row, so the armour row above and the hunger row beside
 * stay aligned. Nothing is ever drawn below the bar — that row is the **XP bar and its level digits**
 * ("血条被经验条数字遮挡", `build=cleanup87`).
 *
 * ## Why it is framed rather than a flat rectangle
 * A single `fill` of solid red read as "纸糊的" in the author's screenshot: no edge, no depth, and it
 * merged with the dark HUD backdrop. This draws a 1 px black border with **clipped corners**, a dark inner
 * track, a vertical gradient for the fill, and a 1 px highlight along the top — the same vocabulary vanilla
 * uses for the hotbar and the XP bar, so it sits in the HUD instead of on top of it.
 *
 * ## Vanilla features deliberately not reproduced
 * | aspect | vanilla hearts | this bar |
 * |---|---|---|
 * | colour | texture swaps (poisoned / withered / frozen / hardcore) | one red gradient |
 * | absorption | a second row of yellow hearts | a gold top edge on the same bar + `+N` inside |
 * | blinking | half-second white flash | the whole fill flashes white |
 */
object BarHealthHud {

    /** Bar width; `9 (icon) + 80 = 89`, inside vanilla's 91 px heart-row footprint. */
    private const val BAR_WIDTH = 80

    /** 9 px matches the heart sprite, so the row stays aligned with vanilla widgets. */
    private const val BAR_HEIGHT = 9

    private val HEART_FULL = Identifier.ofVanilla("hud/heart/full")
    private val HEART_CONTAINER = Identifier.ofVanilla("hud/heart/container")

    /** Vanilla's damage-flash window, so the bar flashes on the same beat the hearts used to. */
    private const val BLINK_TICKS = 20

    /** Ghost catch-up: share of the gap per frame, with a floor so a small chip still moves. */
    private const val GHOST_LERP = 0.2f
    private const val GHOST_MIN_STEP = 0.25f

    /** Frames the ghost is held at the old value after a hit, so the damage is *visible* before it fades. */
    private const val GHOST_HOLD_FRAMES = 6

    // Frame / track
    private val BORDER = 0xFF000000.toInt()
    private val TRACK = 0xFF2B2B2B.toInt()

    // Fill gradient (top → bottom) and the flash colour.
    private val FILL_TOP = 0xFFE0524A.toInt()
    private val FILL_BOTTOM = 0xFF8E1B1B.toInt()
    private val FILL_FLASH = 0xFFFFFFFF.toInt()

    /** 1 px light line along the top of the fill: the cheapest way to read as "solid", not "painted". */
    private val HIGHLIGHT = 0x66FFFFFF

    private val GHOST = 0x99FF8A65.toInt()
    private val ABSORB = 0xFFFFD54F.toInt()
    private val TEXT = 0xFFFFFFFF.toInt()

    /** Trailing ghost value; `NaN` until the first frame (or after [reset]). */
    private var ghost = Float.NaN
    private var holdFrames = 0

    /**
     * Draws the block at ([x], [y]) — the exact coordinates vanilla passed to its heart renderer.
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
        val textRenderer = MinecraftClient.getInstance().textRenderer
        val max = maxHealth.coerceAtLeast(1f)
        val current = health.toFloat().coerceIn(0f, max)

        // --- ghost bookkeeping ----------------------------------------------------------------
        if (ghost.isNaN()) ghost = current
        if (current < ghost) {
            // Damage: hold the old value briefly, then let it drain. This is the only animated part.
            if (holdFrames < GHOST_HOLD_FRAMES) {
                holdFrames++
            } else {
                val gap = ghost - current
                val step = maxOf(gap * GHOST_LERP, GHOST_MIN_STEP)
                ghost = maxOf(ghost - step, current)
            }
        } else {
            ghost = current
            holdFrames = 0
        }

        // --- icon on the left ---------------------------------------------------------------------
        context.drawGuiTexture(HEART_CONTAINER, x, y, 9, 9)
        context.drawGuiTexture(HEART_FULL, x, y, 9, 9)

        // --- bar ----------------------------------------------------------------------------------
        val barX = x + 9
        val barY = y
        drawFrame(context, barX, barY, BAR_WIDTH, BAR_HEIGHT)

        val realRatio = (current / max).coerceIn(0f, 1f)
        val ghostRatio = (ghost / max).coerceIn(0f, 1f)
        val innerX = barX + 1
        val innerY = barY + 1
        val innerW = BAR_WIDTH - 2
        val innerH = BAR_HEIGHT - 2

        val filled = MathHelper.ceil(innerW * realRatio).toInt()
        val ghostEnd = MathHelper.ceil(innerW * ghostRatio).toInt()

        // Ghost first, so the real fill always wins where they overlap.
        if (ghostEnd > filled) {
            context.fill(innerX + filled, innerY, innerX + ghostEnd, innerY + innerH, GHOST)
        }

        val flash = blinking && player.hurtTime > BLINK_TICKS / 2
        if (filled > 0) drawFill(context, innerX, innerY, filled, innerH, flash)

        // Absorption: gold top edge over the fill.
        if (absorption > 0) {
            val absorbRatio = (absorption / max).coerceIn(0f, 1f)
            val absorbEnd = MathHelper.ceil(innerW * minOf(1f, realRatio + absorbRatio)).toInt()
            context.fill(innerX, innerY, innerX + absorbEnd, innerY + 1, ABSORB)
        }

        // --- values, both inside the bar ------------------------------------------------------------
        val value = "${MathHelper.ceil(current)}/${MathHelper.ceil(max)}"
        context.drawText(
            textRenderer,
            value,
            barX + (BAR_WIDTH - textRenderer.getWidth(value)) / 2,
            barY + 1,
            TEXT,
            true,
        )

        if (absorption > 0) {
            val absorbText = "+${MathHelper.ceil(absorption.toFloat())}"
            context.drawText(
                textRenderer,
                absorbText,
                barX + BAR_WIDTH - 2 - textRenderer.getWidth(absorbText),
                barY + 1,
                ABSORB,
                true,
            )
        }
    }

    /**
     * 1 px black outline with **clipped corners**, plus the dark inner track.
     *
     * The corners are left unpainted instead of being filled: a 9 px tall bar with square corners reads as
     * a debug overlay, while a 1 px notch at each corner is what makes it look like a HUD element.
     */
    private fun drawFrame(context: DrawContext, x: Int, y: Int, w: Int, h: Int) {
        context.fill(x + 1, y, x + w - 1, y + 1, BORDER)          // top
        context.fill(x + 1, y + h - 1, x + w - 1, y + h, BORDER)  // bottom
        context.fill(x, y + 1, x + 1, y + h - 1, BORDER)          // left
        context.fill(x + w - 1, y + 1, x + w, y + h - 1, BORDER)  // right
        context.fill(x + 1, y + 1, x + w - 1, y + h - 1, TRACK)   // inner track
    }

    /** Vertical gradient fill for [width] px of the inner track, with a 1 px top highlight. */
    private fun drawFill(context: DrawContext, x: Int, y: Int, width: Int, height: Int, flash: Boolean) {
        if (flash) {
            context.fill(x, y, x + width, y + height, FILL_FLASH)
            return
        }
        for (row in 0 until height) {
            val t = if (height <= 1) 0f else row.toFloat() / (height - 1)
            context.fill(x, y + row, x + width, y + row + 1, lerpColor(FILL_TOP, FILL_BOTTOM, t))
        }
        context.fill(x, y, x + width, y + 1, HIGHLIGHT)
    }

    /** Straight ARGB lerp per channel; `t` is 0 at [a] and 1 at [b]. */
    private fun lerpColor(a: Int, b: Int, t: Float): Int {
        fun mix(shift: Int): Int {
            val ca = (a ushr shift) and 0xFF
            val cb = (b ushr shift) and 0xFF
            return (ca + ((cb - ca) * t).toInt()).coerceIn(0, 255)
        }
        return (mix(24) shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    /** Reset the ghost on world change, so a new life does not inherit the previous trail. */
    fun reset() {
        ghost = Float.NaN
        holdFrames = 0
    }
}
