package dev.firefly.simpletweaks.client.hud

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.Identifier
import net.minecraft.util.math.MathHelper

/**
 * Replaces vanilla's row of ten hearts with **one bar**: `♥ [████ 19/20 ████]`.
 *
 * ## Why replace instead of appending
 * Vanilla draws ten 8×9 heart sprites per row, so a modded player with 40+ max health needs three rows. A
 * bar carries the same information in one row and stays readable at any scale. The mixin that calls this
 * **cancels** `InGameHud#renderHealthBar`; nothing else in the HUD is touched (armour, hunger, air, XP and
 * mount health keep their vanilla renderers). [dev.firefly.simpletweaks.core.config.GeneralConfig.barHealthHud]
 * turns it off, in which case the mixin does not cancel and vanilla hearts are drawn untouched.
 *
 * ## Layout (GUI pixels)
 * ```
 * x     x+11              x+11+BAR_WIDTH
 * ♥     [███  19/20  ███]
 * ```
 * The heart is the real vanilla sprite (`hud/heart/full`), so it matches the rest of the screen. The value
 * is drawn **inside** the bar, centred — outside, the block grew to ~130 px and could collide with the
 * hotbar edge at large GUI scales (`build=cleanup86`).
 *
 * ## Red, not a green→red gradient
 * Author's call: green-at-full reads as "stamina" in every other game. State is still readable without
 * colour, because the fill is proportional and the exact number is printed inside the bar.
 *
 * ## Instant fill + a trailing ghost (why there is no "delay")
 * The **fill and the number are the real health, drawn on the same frame** — vanilla's own animation field
 * (`renderHealthValue`) lives inside the method we cancel, so nothing delays the reading. What animates is
 * a separate **ghost** slice that starts at the old value and catches up over ~0.5 s. That gives the
 * "damage landed" motion (`build=cleanup86`'s 衔接 request) without making the bar lag behind the
 * number — the exact failure the author reported as "血条更新还有延迟" when it *was* the fill that lerped.
 *
 * ## Vanilla features deliberately not reproduced
 * | aspect | vanilla hearts | this bar |
 * |---|---|---|
 * | colour | texture swaps (poisoned / withered / frozen / hardcore) | one red fill |
 * | absorption | a second row of yellow hearts | a gold overlay on the same bar + `+N` under it |
 * | blinking | half-second white flash | the whole fill flashes white |
 */
object BarHealthHud {

    /** Width of the bar, icon excluded. Fits `4096/4096` plus padding at GUI scale 2. */
    private const val BAR_WIDTH = 86

    /** 9 px matches the heart sprite, so the row stays aligned with vanilla widgets. */
    private const val BAR_HEIGHT = 9

    /** Gap between the icon and the bar. */
    private const val GAP = 2

    private val HEART_FULL = Identifier.ofVanilla("hud/heart/full")
    private val HEART_CONTAINER = Identifier.ofVanilla("hud/heart/container")

    /** Vanilla's damage-flash window, so the bar flashes on the same beat the hearts used to. */
    private const val BLINK_TICKS = 20

    /** Ghost catch-up: share of the gap per frame, with a floor so a small chip still moves. */
    private const val GHOST_LERP = 0.2f
    private const val GHOST_MIN_STEP = 0.25f

    /** Frames the ghost is held at the old value after a hit, so the damage is *visible* before it fades. */
    private const val GHOST_HOLD_FRAMES = 6

    private val BAR_BG = 0xCC000000.toInt()
    private val FILL = 0xFFB02B2B.toInt()
    private val FILL_FLASH = 0xFFFFFFFF.toInt()
    private val GHOST = 0xAAFF7B54.toInt()
    private val ABSORB = 0xFFFFD54F.toInt()
    private val TEXT = 0xFFFFFFFF.toInt()

    /** Trailing ghost value; `NaN` until the first frame (or after [reset]). */
    private var ghost = Float.NaN
    private var holdFrames = 0

    /**
     * Draws the block at ([x], [y]) — the exact coordinates vanilla passed to its heart renderer.
     *
     * @param health the value vanilla would have animated; it is the **real** health here
     * @param lastHealth the value before this hit
     */
    fun render(
        context: DrawContext,
        player: PlayerEntity,
        x: Int,
        y: Int,
        maxHealth: Float,
        lastHealth: Int,
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
            // Healing (or a full bar): no trailing anything.
            ghost = current
            holdFrames = 0
        }

        // --- icon -------------------------------------------------------------------------------
        context.drawGuiTexture(HEART_CONTAINER, x, y, 9, 9)
        context.drawGuiTexture(HEART_FULL, x, y, 9, 9)

        // --- bar --------------------------------------------------------------------------------
        val barX = x + 9 + GAP
        val barY = y
        context.fill(barX, barY, barX + BAR_WIDTH, barY + BAR_HEIGHT, BAR_BG)

        val realRatio = (current / max).coerceIn(0f, 1f)
        val ghostRatio = (ghost / max).coerceIn(0f, 1f)
        val filled = MathHelper.ceil(BAR_WIDTH * realRatio).toInt()
        val ghostEnd = MathHelper.ceil(BAR_WIDTH * ghostRatio).toInt()

        // Ghost first, so the real fill always wins where they overlap.
        if (ghostEnd > filled) {
            context.fill(barX + filled, barY, barX + ghostEnd, barY + BAR_HEIGHT, GHOST)
        }

        val flash = blinking && player.hurtTime > BLINK_TICKS / 2
        if (filled > 0) {
            context.fill(barX, barY, barX + filled, barY + BAR_HEIGHT, if (flash) FILL_FLASH else FILL)
        }

        // Absorption: gold overlay on the top edge, plus the amount under the bar.
        if (absorption > 0) {
            val absorbRatio = (absorption / max).coerceIn(0f, 1f)
            val absorbEnd = MathHelper.ceil(BAR_WIDTH * minOf(1f, realRatio + absorbRatio)).toInt()
            context.fill(barX, barY, barX + absorbEnd, barY + 2, ABSORB)
        }

        // --- value, inside the bar and centred ----------------------------------------------------
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
                barX + (BAR_WIDTH - textRenderer.getWidth(absorbText)) / 2,
                barY + BAR_HEIGHT + 1,
                ABSORB,
                true,
            )
        }
    }

    /** Reset the ghost on world change, so a new life does not inherit the previous trail. */
    fun reset() {
        ghost = Float.NaN
        holdFrames = 0
    }
}
