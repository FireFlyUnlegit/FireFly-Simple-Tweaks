package dev.firefly.simpletweaks.client.hud

import net.minecraft.client.gui.DrawContext
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.MathHelper

/**
 * Hunger as **one framed bar**, right-aligned, mirroring the health bar.
 *
 * ## Why it is mirrored instead of left-aligned
 * Vanilla puts the food icons on the **right** of the century-old 182 px HUD block, so the eye finds them
 * there. Moving hunger to the left would break that muscle memory for a cosmetic gain, so the block is the
 * same widget (`icon + bar`, 89 px) anchored so its **right** edge lands where vanilla's food row ends.
 *
 * ## The number
 * `foodLevel` is 0..20 and is the only value that gates sprinting and regeneration; saturation is an
 * internal buffer that the player cannot act on. The bar shows `foodLevel / 20` and the number prints
 * `foodLevel`, which is exactly the vanilla information.
 */
object HungerHud {

    private val FILL_TOP = 0xFFE8A33D.toInt()
    private val FILL_BOTTOM = 0xFF8A4A12.toInt()
    private val TEXT = 0xFFFFFFFF.toInt()

    /** Vanilla's hunger scale: `foodLevel` is 0..20. */
    private const val REFERENCE = 20f

    fun render(context: DrawContext, player: PlayerEntity, x: Int, y: Int) {
        val food = player.hungerManager.foodLevel
        val barX = x + HudBar.ICON
        context.drawGuiTexture(HudBar.FOOD_FULL, x, y, 9, 9)
        HudBar.frame(context, barX, y, HudBar.WIDTH, HudBar.HEIGHT)
        HudBar.fill(
            context, barX, y, HudBar.WIDTH, HudBar.HEIGHT,
            (food / REFERENCE).coerceIn(0f, 1f), FILL_TOP, FILL_BOTTOM,
        )
        HudBar.text(context, MathHelper.ceil(food.toFloat()).toString(), barX, y, HudBar.WIDTH, TEXT)
    }
}
