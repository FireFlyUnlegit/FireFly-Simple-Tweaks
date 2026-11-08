package dev.firefly.simpletweaks.client.hud

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.entity.player.PlayerEntity

/**
 * Entry point for the whole bar HUD, called from **`InGameHud#renderMainHud`'s TAIL**.
 *
 * ## Why not the health bar's own pass any more (`build=cleanup93`)
 * Everything used to be drawn from inside `renderHealthBar`, which was convenient (we already replaced that
 * method) and completely wrong in one case: **vanilla does not draw the health bar in creative or spectator
 * mode**, so the armour and hunger bars that lived in the same pass vanished with it. The author tests in
 * creative (4096 max health) and reported exactly that: "护甲值也是有的时候才会显示".
 *
 * `renderMainHud` runs in every game mode and on every frame, so all four bars are drawn there, and the
 * three vanilla renderers (`renderHealthBar`, `renderArmor`, `renderFood`) are reduced to **suppressors**
 * that only cancel — which also makes them harmless if another mod pre-empts them.
 *
 * ## Coordinates
 * The HUD block is centred and vanilla's own constants are stable: hearts start at `width / 2 - 91` and the
 * food row ends at `width / 2 + 91`, with the survival row at `height - 39`. Computing them here means this
 * file does not depend on any vanilla method's parameter order (three of them are ambiguous from bytecode
 * alone — the previous rounds lost time to exactly that).
 *
 * ## The player may be absent
 * `MinecraftClient#player` is null on the title screen and during world load; `renderMainHud` still runs
 * afterwards for the HUD, so a null check is required rather than defensive style.
 */
object BarsHud {

    /** Vanilla's survival-row baseline: `height - 39`. */
    private const val ROW_FROM_BOTTOM = 39

    /** Half of vanilla's 182 px HUD block. */
    private const val HALF_BLOCK = 91

    private const val ROW = 10

    fun render(context: DrawContext) {
        if (!dev.firefly.simpletweaks.core.config.GeneralConfig.barHealthHud) return
        val player: PlayerEntity = MinecraftClient.getInstance().player ?: return
        if (player.isSpectator) return

        val width = context.getScaledWindowWidth()
        val y = context.getScaledWindowHeight() - ROW_FROM_BOTTOM
        val leftX = width / 2 - HALF_BLOCK
        val rightEdge = width / 2 + HALF_BLOCK

        val absorption = player.absorptionAmount.toInt()

        BarHealthHud.render(
            context,
            player,
            leftX,
            y,
            player.maxHealth,
            Math.ceil(player.health.toDouble()).toInt(),
            absorption,
            // The flash used to come from vanilla's `blinking` argument; at this seam there is none, so it
            // is derived from the hurt timer, which is the same 20-tick window vanilla used.
            player.hurtTime > ROW,
        )
    }
}
