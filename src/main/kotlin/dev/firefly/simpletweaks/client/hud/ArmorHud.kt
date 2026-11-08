package dev.firefly.simpletweaks.client.hud

import net.minecraft.client.gui.DrawContext
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.MathHelper

/**
 * Armour as **one framed bar**, with armour **toughness** as a thin cyan line along its bottom edge.
 *
 * ## Why toughness is a line and not a second bar
 * Vanilla draws toughness as small pips *above* the armour icons — a second visual language for a number
 * that only matters against strong hits. Here both live in one widget: the bar is the armour value
 * (`min(armor, 20) / 20`, i.e. full at vanilla's damage-reduction wall of 20), the line is
 * `min(toughness, 20) / 20`, and the number printed inside is the armour value.
 *
 * ## The 20 cap
 * Armour above 20 keeps reducing damage in vanilla, but a bar that never fills past 20 % of its width for a
 * 100-armour set reads as broken. The bar saturates at 20 and the **number** carries the truth — which is
 * also why the value is printed rather than inferred from the fill.
 *
 * Drawn from the health bar's pass, not from a `renderArmor` injection; see [BarHealthHud] for why.
 */
object ArmorHud {

    private val FILL_TOP = 0xFFBFD4E6.toInt()
    private val FILL_BOTTOM = 0xFF5A6E82.toInt()
    private val TOUGHNESS = 0xFF7FE0E8.toInt()
    private val TEXT = 0xFFFFFFFF.toInt()

    /** Armour value that fills the bar: vanilla's damage-reduction wall. */
    private const val REFERENCE = 20f

    fun render(context: DrawContext, player: PlayerEntity, x: Int, y: Int) {
        // **Always** drawn, even at 0 armour: an empty bar with `0` is self-explanatory, while a bar that
        // silently disappears is indistinguishable from "the feature is broken" (author: 没有 armor,
        // `build=cleanup91`). The number, not the presence of the widget, carries the state.
        val armor = player.armor

        // [icon] hangs outside: the caller passes the icon column, the bar starts right after it.
        val barX = x + HudBar.ICON
        HudBar.icon(context, HudBar.ARMOR_EMPTY, HudBar.ARMOR_FULL, x, y)
        HudBar.frame(context, barX, y, HudBar.WIDTH, HudBar.HEIGHT)
        HudBar.fill(
            context, barX, y, HudBar.WIDTH, HudBar.HEIGHT,
            (armor / REFERENCE).coerceIn(0f, 1f), FILL_TOP, FILL_BOTTOM,
        )

        val toughness = player.getAttributeValue(
            net.minecraft.entity.attribute.EntityAttributes.GENERIC_ARMOR_TOUGHNESS
        )
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
