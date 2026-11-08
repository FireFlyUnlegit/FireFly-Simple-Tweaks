package dev.firefly.simpletweaks.client.hud

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.util.Identifier
import net.minecraft.util.math.MathHelper

/**
 * The drawing vocabulary shared by every bar this mod puts on the HUD.
 *
 * ## Why one helper instead of five copies
 * Health, absorption, armour (with toughness) and hunger all render the same widget: a clipped-corner
 * border, a dark inner track, a vertical gradient fill, a 1 px top highlight and centred text. Five copies
 * of that would drift the moment one of them is tweaked — and "the hunger bar has corners the health bar
 * does not" is exactly the kind of inconsistency nobody files a bug for.
 *
 * ## The look, and why it is not a flat rectangle
 * A single `fill` read as "纸糊的" in the author's screenshot: no edge, no depth, merging with the dark HUD
 * backdrop (`build=cleanup88`). The pieces below are the same vocabulary vanilla uses for the hotbar and the
 * XP bar, so a bar sits *in* the HUD rather than on top of it.
 *
 * ## Geometry
 * All helpers take an outer rectangle and keep their ink inside it. Corners are **left unpainted** on
 * purpose: a square 9 px bar reads as a debug overlay, a 1 px notch reads as a HUD element.
 */
object HudBar {

    /** 9 px matches the vanilla heart / armour / food sprite height. */
    const val HEIGHT = 9

    /** Icon column width: one 9×9 sprite. */
    const val ICON = 9

    /** Width of a left-anchored block: icon + bar. Kept inside vanilla's 91 px heart-row footprint. */
    const val BLOCK = 89

    /**
     * How far **up** every bar row is lifted from vanilla's baseline (`height - 39`).
     *
     * Vanilla's hearts are 9 px tall and sit at `height - 39`, which leaves their bottom row flush against
     * the experience bar and its level digits — the reported "血条被经验等级挡掉一点点"
     * (`build=cleanup89`). Lifting by 4 px clears the digits while still leaving the block visually attached
     * to the hotbar (a full row's 10 px would leave a visible gap under the bars).
     */
    const val LIFT = 4

    /** Bar width used by every block (`BLOCK - ICON`). */
    const val WIDTH = BLOCK - ICON

    val HEART_FULL = Identifier.ofVanilla("hud/heart/full")
    val HEART_ABSORBING = Identifier.ofVanilla("hud/heart/absorbing_full")
    val ARMOR_FULL = Identifier.ofVanilla("hud/armor_full")
    val FOOD_FULL = Identifier.ofVanilla("hud/food_full")

    private val BORDER = 0xFF000000.toInt()
    private val TRACK = 0xFF2B2B2B.toInt()
    private val HIGHLIGHT = 0x66FFFFFF

    /** 1 px black outline with clipped corners, plus the dark inner track. */
    fun frame(context: DrawContext, x: Int, y: Int, w: Int, h: Int) {
        context.fill(x + 1, y, x + w - 1, y + 1, BORDER)
        context.fill(x + 1, y + h - 1, x + w - 1, y + h, BORDER)
        context.fill(x, y + 1, x + 1, y + h - 1, BORDER)
        context.fill(x + w - 1, y + 1, x + w, y + h - 1, BORDER)
        context.fill(x + 1, y + 1, x + w - 1, y + h - 1, TRACK)
    }

    /**
     * Vertical gradient fill inside a framed bar.
     *
     * @param ratio 0..1 of the **inner** track to fill
     */
    fun fill(
        context: DrawContext,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        ratio: Float,
        top: Int,
        bottom: Int,
        flash: Boolean = false,
    ) {
        val innerX = x + 1
        val innerY = y + 1
        val innerW = w - 2
        val innerH = h - 2
        val filled = MathHelper.ceil(innerW * ratio.coerceIn(0f, 1f)).toInt()
        if (filled <= 0) return
        if (flash) {
            context.fill(innerX, innerY, innerX + filled, innerY + innerH, 0xFFFFFFFF.toInt())
            return
        }
        for (row in 0 until innerH) {
            val t = if (innerH <= 1) 0f else row.toFloat() / (innerH - 1)
            context.fill(innerX, innerY + row, innerX + filled, innerY + row + 1, lerp(top, bottom, t))
        }
        context.fill(innerX, innerY, innerX + filled, innerY + 1, HIGHLIGHT)
    }

    /** Centred text inside a framed bar. */
    fun text(context: DrawContext, value: String, x: Int, y: Int, w: Int, color: Int) {
        val renderer = MinecraftClient.getInstance().textRenderer
        context.drawText(
            renderer,
            value,
            x + (w - renderer.getWidth(value)) / 2,
            y + 1,
            color,
            true,
        )
    }

    /** Right-aligned text inside a framed bar (used for the absorption figure). */
    fun textRight(context: DrawContext, value: String, x: Int, y: Int, w: Int, color: Int) {
        val renderer = MinecraftClient.getInstance().textRenderer
        context.drawText(renderer, value, x + w - 2 - renderer.getWidth(value), y + 1, color, true)
    }

    /** Straight ARGB lerp per channel; `t` is 0 at [a] and 1 at [b]. */
    private fun lerp(a: Int, b: Int, t: Float): Int {
        fun mix(shift: Int): Int {
            val ca = (a ushr shift) and 0xFF
            val cb = (b ushr shift) and 0xFF
            return (ca + ((cb - ca) * t).toInt()).coerceIn(0, 255)
        }
        return (mix(24) shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }
}
