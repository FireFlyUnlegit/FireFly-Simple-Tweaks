package dev.firefly.simpletweaks.client.hud

import net.minecraft.client.gui.DrawContext
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.MathHelper

/**
 * Armour as **one framed bar**, with armour **toughness** as a thin line along its bottom edge.
 *
 * ## Why toughness is a line and not a second bar
 * Vanilla draws toughness as small pips *above* the armour icons — a second visual language for a number
 * that only matters against strong hits. Here the two live in one widget: the bar is the armour value
 * (`min(armor, 20) / 20`, i.e. full at vanilla's damage-reduction wall of 20), the cyan line at its bottom
 * is `min(toughness, 20) / 20`, and the number printed inside is the armour value. One row, both numbers,
 * no extra vertical space — the HUD is already dense at 10 px rows.
 *
 * ## The 20 cap
 * Armour above 20 keeps reducing damage in vanilla (the curve continues), but a bar that never fills past
 * 20 % of its width for a 100-armour set would read as broken. The bar therefore saturates at 20 and the
 * **number** carries the truth; that is also why the value is printed rather than inferred from the fill.
 */
object ArmorHud {

    private val FILL_TOP = 0xFFBFD4E6.toInt()
    private val FILL_BOTTOM = 0xFF5A6E82.toInt()
    private val TOUGHNESS = 0xFF7FE0E8.toInt()
    private val TEXT = 0xFFFFFFFF.toInt()

    /** Armour value that fills the bar: vanilla's damage-reduction wall. */
    private const val REFERENCE = 20f

    fun render(context: DrawContext, player: PlayerEntity, x: Int, y: Int) {
        val armor = player.armor
        if (armor <= 0) return

        val barX = x + HudBar.ICON
        context.drawGuiTexture(HudBar.ARMOR_FULL, x, y, 9, 9)
        HudBar.frame(context, barX, y, HudBar.WIDTH, HudBar.HEIGHT)
        HudBar.fill(
            context, barX, y, HudBar.WIDTH, HudBar.HEIGHT,
            (armor / REFERENCE).coerceIn(0f, 1f), FILL_TOP, FILL_BOTTOM,
        )

        // Toughness: a 1 px line along the bottom of the inner track, same scale as armour.
        val toughness = player.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ARMOR_TOUGHNESS)
        if (toughness > 0.0) {
            val innerW = HudBar.WIDTH - 2
            val px = MathHelper.ceil(innerW * (toughness / REFERENCE).coerceIn(0.0, 1.0)).toInt()
            if (px > 0) {
                val lineY = y + HudBar.HEIGHT - 2
                context.fill(barX + 1, lineY, barX + 1 + px, lineY + 1, TOUGHNESS)
            }
        }

        HudBar.text(context, armor.toString(), barX, y, HudBar.WIDTH, TEXT)
    }
}
