package dev.firefly.simpletweaks.client.hud

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.util.Identifier
import net.minecraft.util.math.MathHelper

/**
 * The drawing vocabulary shared by every bar this mod puts on the HUD.
 *
 * ## Why one helper instead of four copies
 * Health, absorption, armour (with toughness) and hunger all render the same widget: a container sprite,
 * a clipped-corner border, a dark inner track, a vertical gradient fill and centred text. Four copies
 * would drift the moment one is tweaked — and "the hunger bar has corners the health bar does not" is
 * exactly the kind of inconsistency nobody files a bug for.
 *
 * ## Icons are drawn as container + overlaid fill, like vanilla does
 * A single `hud/heart/full` on the HUD background looked flat ("icon 都没质感了", `build=cleanup90`):
 * vanilla always draws the **empty container first** and the full/half sprite on top, which is what gives
 * the icon its outline and its dark interior. Every icon here is therefore a pair:
 * `hud/heart/container` + `hud/heart/full`, `hud/armor_empty` + `hud/armor_full`,
 * `hud/food_empty` + `hud/food_full`.
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

    /** Width of a block: icon + bar. Kept inside vanilla's 91 px heart-row footprint. */
    const val BLOCK = 89

    /** Bar width used by every block. */
    const val WIDTH = BLOCK - ICON

    /** Container + fill sprite pairs, in the order vanilla draws them. */
    val HEART_CONTAINER = Identifier.ofVanilla("hud/heart/container")
    val HEART_FULL = Identifier.ofVanilla("hud/heart/full")
    val HEART_ABSORBING = Identifier.ofVanilla("hud/heart/absorbing_full")
    val ARMOR_EMPTY = Identifier.ofVanilla("hud/armor_empty")
    val ARMOR_FULL = Identifier.ofVanilla("hud/armor_full")
    val FOOD_EMPTY = Identifier.ofVanilla("hud/food_empty")
    val FOOD_FULL = Identifier.ofVanilla("hud/food_full")

    private val BORDER = 0xFF000000.toInt()
    private val TRACK = 0xFF2B2B2B.toInt()
    private val HIGHLIGHT = 0x66FFFFFF

    /** Container sprite then fill sprite — vanilla's layering, which is what gives the icon depth. */
    fun icon(context: DrawContext, container: Identifier, fill: Identifier, x: Int, y: Int) {
        context.drawGuiTexture(container, x, y, 9, 9)
        context.drawGuiTexture(fill, x, y, 9, 9)
    }

    /** 1 px black outline with clipped corners, plus the dark inner track. */
    fun frame(context: DrawContext, x: Int, y: Int, w: Int, h: Int) {
        context.fill(x + 1, y, x + w - 1, y + 1, BORDER)
        context.fill(x + 1, y + h - 1, x + w - 1, y + h, BORDER)
        context.fill(x, y + 1, x + 1, y + h - 1, BORDER)
        context.fill(x + w - 1, y + 1, x + w, y + h - 1, BORDER)
        context.fill(x + 1, y + 1, x + w - 1, y + h - 1, TRACK)
    }

    /** Vertical gradient fill inside a framed bar. */
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
        context.drawText(renderer, value, x + (w - renderer.getWidth(value)) / 2, y + 1, color, true)
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
