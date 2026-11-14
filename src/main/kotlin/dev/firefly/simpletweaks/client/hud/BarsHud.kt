package dev.firefly.simpletweaks.client.hud

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.entity.player.PlayerEntity

/**
 * Draws every bar HUD from `InGameHud#renderMainHud`'s tail — the only pass that runs in all game modes
 * (vanilla skips the health bar in creative/spectator, which used to take the whole block with it).
 */
object BarsHud {

    /** Vanilla survival-row baseline: `height - 39`. */
    private const val ROW_FROM_BOTTOM = 39

    /** Half of vanilla's 182 px HUD block. */
    private const val HALF_BLOCK = 91

    fun render(context: DrawContext) {
        if (!dev.firefly.simpletweaks.core.config.GeneralConfig.barHealthHud) return
        val player: PlayerEntity = MinecraftClient.getInstance().player ?: return
        if (player.isSpectator) return

        val width = context.getScaledWindowWidth()
        val leftX = width / 2 - HALF_BLOCK
        val y = context.getScaledWindowHeight() - ROW_FROM_BOTTOM

        BarHealthHud.render(
            context,
            player,
            leftX,
            y,
            player.maxHealth,
            player.health,
            player.absorptionAmount,
            // Same 20-tick window vanilla used for the hearts' flash.
            player.hurtTime > 10,
        )
    }
}
