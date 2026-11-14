package dev.firefly.simpletweaks.client.hud

import net.minecraft.client.gui.DrawContext
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.player.PlayerEntity

/** Armour bar; hidden when the player has no armour. */
object ArmorHud {

    private val FILL_TOP = 0xFFBFD4E6.toInt()
    private val FILL_BOTTOM = 0xFF5A6E82.toInt()
    private val TEXT = 0xFFFFFFFF.toInt()

    /** Armour that fills the bar; the number carries anything above it. */
    private const val REFERENCE = 20f

    private val smooth = HudBar.Smooth()

    fun render(context: DrawContext, player: PlayerEntity, x: Int, y: Int) {
        val barX = x + HudBar.ICON
        // Attribute value, not `player.armor`: that one floors, and our enchantments add fractions.
        val armor = player.getAttributeValue(EntityAttributes.GENERIC_ARMOR)
        if (armor <= 0.0) {
            smooth.reset()
            return
        }
        val shown = smooth.update(armor.toFloat())

        HudBar.icon(context, HudBar.ARMOR_EMPTY, HudBar.ARMOR_FULL, x, y)
        HudBar.frame(context, barX, y, HudBar.WIDTH, HudBar.HEIGHT)
        HudBar.fill(
            context, barX, y, HudBar.WIDTH, HudBar.HEIGHT,
            (shown / REFERENCE).coerceIn(0f, 1f), FILL_TOP, FILL_BOTTOM,
        )
        HudBar.text(context, HudBar.number(armor), barX, y, HudBar.WIDTH, TEXT)
    }

    fun reset() {
        smooth.reset()
    }
}
