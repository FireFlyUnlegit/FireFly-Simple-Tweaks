package dev.firefly.simpletweaks.client.hud

import net.minecraft.client.gui.DrawContext
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.MathHelper

/**
 * Draws **all four HUD bars in one place**, from the health bar's own render pass.
 *
 * ## Why everything hangs off `renderHealthBar`
 * The mixins that cancel `renderArmor` / `renderFood` are one conflict away from not running: a modpack
 * that also targets those methods (InventoryHUD+ does exactly that in the author's client) can cancel at
 * HEAD first, in which case anything drawn **inside** them never appears. The health bar's own pass is the
 * one seam this mod genuinely owns — it was already replaced in `build=cleanup85` and it demonstrably
 * runs — so armour and hunger are drawn here too. The other two mixins are reduced to "cancel the vanilla
 * icons, draw nothing", which makes them **optional**: if they are pre-empted, the player sees vanilla
 * icons *plus* our bars instead of empty space (the reported "没发现护甲值显示").
 *
 * ## Rows (baseline = the `y` vanilla passes to the health bar)
 * | row | content |
 * |---|---|
 * | `y - 20` | armour, **only when absorption also occupies `y - 10`** |
 * | `y - 10` | absorption (gold), else armour |
 * | `y` | health |
 *
 * Hunger sits on the health row, right-aligned, with its icon on the **right** of the bar
 * (`build=cleanup90`): the block is anchored to vanilla's right edge (`width / 2 + 91`), so an icon on the
 * left would point away from the screen edge the player's eye uses.
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
     * Draws the whole HUD block at ([x], [y]) — the coordinates vanilla passed for the heart row.
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

        // The bar's own left edge is the HUD anchor; the icon hangs outside it (author, cleanup91).
        val barX = x
        val iconX = x - HudBar.ICON

        // --- armour first, so it is underneath the bars it shares rows with -------------------------
        // Always drawn (even at 0 armour): see ArmorHud's KDoc.
        ArmorHud.render(context, player, x - HudBar.ICON, y - (if (absorption > 0) 20 else 10))

        // --- health --------------------------------------------------------------------------------
        HudBar.icon(context, HudBar.HEART_CONTAINER, HudBar.HEART_FULL, iconX, y)
        HudBar.frame(context, barX, y, HudBar.WIDTH, HudBar.HEIGHT)

        val innerW = HudBar.WIDTH - 2
        val realRatio = (current / max).coerceIn(0f, 1f)
        val filledPx = MathHelper.ceil(innerW * realRatio).toInt()
        val ghostPx = MathHelper.ceil(innerW * (ghost / max).coerceIn(0f, 1f)).toInt()

        if (ghostPx > filledPx) {
            context.fill(barX + 1 + filledPx, y + 1, barX + 1 + ghostPx, y + HudBar.HEIGHT - 1, GHOST)
        }

        val flash = blinking && player.hurtTime > BLINK_TICKS / 2
        HudBar.fill(context, barX, y, HudBar.WIDTH, HudBar.HEIGHT, realRatio, FILL_TOP, FILL_BOTTOM, flash)
        HudBar.text(context, "${MathHelper.ceil(current)}/${MathHelper.ceil(max)}", barX, y, HudBar.WIDTH, TEXT)

        // --- absorption: same bar, one row up, gold ------------------------------------------------
        if (absorption > 0) {
            val absorbY = y - 10
            HudBar.icon(context, HudBar.HEART_CONTAINER, HudBar.HEART_ABSORBING, iconX, absorbY)
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

        // --- hunger: mirrored to the right edge, icon on the right ---------------------------------
        val rightEdge = context.getScaledWindowWidth() / 2 + 91
        HungerHud.renderRightAligned(context, player, rightEdge, y)
    }

    /** Reset the ghost on world change, so a new life does not inherit the previous trail. */
    fun reset() {
        ghost = Float.NaN
        holdFrames = 0
    }
}
