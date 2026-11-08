package dev.firefly.simpletweaks.client.hud

import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.Identifier
import net.minecraft.util.math.MathHelper

/**
 * Replaces vanilla's row of ten hearts with **one bar**: `♥ [████████░░] 18/20`.
 *
 * ## Why replace instead of appending
 * Vanilla draws ten separate 8×9 heart sprites per row, so its health block is ~90 px wide and one row
 * tall for a normal player, two once absorption is present. A bar carries the same information in ~80 px
 * and stays readable when a modded player has 40+ max health (vanilla would need three rows of hearts for
 * that). The mixin that calls this **cancels** `InGameHud#renderHealthBar`; nothing else in the HUD is
 * touched (armour, hunger, air, XP, mount health all keep their vanilla renderers).
 *
 * ## Layout (all sizes in GUI pixels)
 * ```
 * x        x+9            x+9+BAR_WIDTH
 * ♥        ████████████   18/20
 * ```
 * The heart is the real vanilla sprite (`hud/heart/full`), so it matches the resource-pack style of
 * everything else on screen; the value is the first `<int>/<int>` the player can read at a glance.
 *
 * ## Numbers that are deliberately *not* one-to-one with vanilla
 * | aspect | vanilla hearts | this bar |
 * |---|---|---|
 * | colour | texture swaps (poisoned / withered / frozen) | a single green→red gradient by ratio |
 * | absorption | a second row of yellow hearts | a gold overlay on the same bar |
 * | blinking | half-second flash on damage | the whole fill flashes (same 20-tick window) |
 *
 * Keeping vanilla's status-effect textures would mean reproducing five heart variants; the gradient
 * answers the same question ("how much am I missing?") with one number and one colour.
 */
object BarHealthHud {

    /** Width of the bar itself, excluding the icon and the value text. */
    private const val BAR_WIDTH = 72

    /** Height of the bar; 5 px keeps it inside the 9 px heart row. */
    private const val BAR_HEIGHT = 5

    /** Gap between the icon and the bar, and between the bar and the value. */
    private const val GAP = 2

    private val HEART_FULL = Identifier.ofVanilla("hud/heart/full")
    private val HEART_CONTAINER = Identifier.ofVanilla("hud/heart/container")
    /** Vanilla's own damage-blink window, so the bar flashes on the same beat the hearts used to. */
    private const val BLINK_TICKS = 20

    /**
     * Draws the whole block at ([x], [y]) — the exact coordinates vanilla passed to its heart renderer.
     *
     * @param health vanilla passes the **displayed** value (it animates towards the real one)
     * @param lastHealth the value before this hit, used for the flash
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
        val textRenderer: TextRenderer = net.minecraft.client.MinecraftClient.getInstance().textRenderer
        val max = maxHealth.coerceAtLeast(1f)
        val ratio = (health / max).coerceIn(0f, 1f)
        val absorbing = absorption > 0

        // --- icon -------------------------------------------------------------------------------
        context.drawGuiTexture(if (absorbing) HEART_FULL else HEART_CONTAINER, x, y, 9, 9)
        if (!absorbing) {
            // The container is the empty heart outline; drawing the full heart on top of it fills it, so
            // a wounded player still sees a readable icon without reproducing the half-heart variants.
            context.drawGuiTexture(HEART_FULL, x, y, 9, 9)
        }

        // --- bar --------------------------------------------------------------------------------
        val barX = x + 9 + GAP
        val barY = y + (9 - BAR_HEIGHT) / 2

        // Background: black at 60 % alpha, matching the vanilla container's darkness closely enough.
        context.fill(barX, barY, barX + BAR_WIDTH, barY + BAR_HEIGHT, 0x99000000.toInt())

        val filled = MathHelper.ceil(BAR_WIDTH * ratio).toInt()
        if (filled > 0) {
            // Flash when the health just dropped, on the same 20-tick window vanilla uses.
            val flash = blinking && player.hurtTime > BLINK_TICKS / 2
            context.fill(barX, barY, barX + filled, barY + BAR_HEIGHT, healthColor(ratio, flash))
        }

        // Absorption is a gold overlay over the health fill: it is spent *after* the red hearts, so it
        // must be visible on top rather than replacing the health colour.
        if (absorbing) {
            val absorbRatio = (absorption / max).coerceIn(0f, 1f)
            val absorbWidth = MathHelper.ceil(BAR_WIDTH * minOf(1f, ratio + absorbRatio)).toInt()
            context.fill(barX, barY, barX + absorbWidth, barY + 1, 0xFFFFD54F.toInt())
        }

        // --- value ------------------------------------------------------------------------------
        val value = "${MathHelper.ceil(health.toFloat())}/${MathHelper.ceil(max)}"
        context.drawText(
            textRenderer,
            value,
            barX + BAR_WIDTH + GAP,
            y + 1,
            0xFFFFFFFF.toInt(),
            true,
        )

        // --- absorption amount -------------------------------------------------------------------
        if (absorbing) {
            // Gold number under the health value, so the two resources never collide.
            context.drawText(
                textRenderer,
                "+${MathHelper.ceil(absorption.toFloat())}",
                barX + BAR_WIDTH + GAP,
                y + 10,
                0xFFFFD54F.toInt(),
                true,
            )
        }
    }

    /**
     * Green → yellow → red by missing health, with a white flash on damage.
     *
     * The three stops are hand-picked rather than interpolated through HSV: a bar that is *always* some
     * shade of yellow is unreadable, and vanilla's own palette (green hearts, red poison) is what players
     * already associate with "healthy" and "hurting".
     */
    private fun healthColor(ratio: Float, flash: Boolean): Int {
        if (flash) return 0xFFFFFFFF.toInt()
        return when {
            ratio > 0.6f -> 0xFF35C759.toInt()
            ratio > 0.3f -> 0xFFE8B923.toInt()
            else -> 0xFFD64541.toInt()
        }
    }
}
