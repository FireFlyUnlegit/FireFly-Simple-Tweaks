package dev.firefly.simpletweaks.mixin.client;

import dev.firefly.simpletweaks.client.hud.BarHealthHud;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces vanilla's ten-heart row with {@link BarHealthHud}'s single bar.
 *
 * <h2>Why HEAD + cancel rather than a redirect</h2>
 * {@code renderHealthBar} draws the container, both rows of hearts, the absorption row, the blink flash
 * and the mount-health fallback in one body. There is no single call to redirect that would remove all
 * of it, and leaving any one piece in place produces a hybrid (bar **and** five leftover hearts) that
 * looks broken. Cancelling at HEAD and drawing the whole block ourselves is the only shape where "one
 * bar" is literally true.
 *
 * <h2>Override audit (`AGENTS.md` §4.3)</h2>
 * The target is {@code InGameHud}'s own {@code private renderHealthBar(DrawContext, PlayerEntity, int,
 * int, int, int, float, int, int, int, boolean)} — verified in the 1.21.1 named jar; it is private, so
 * no subclass can override or shadow it, and {@code InGameHud} has no subclasses in the game. The only
 * other caller of the heart sprites is {@code renderMountHealth}, which is a different method and is
 * deliberately left alone (a ridden mob keeps its vanilla hearts).
 *
 * <h2>Why the signature is spelled out in full</h2>
 * {@code renderHealthBar} has no other overload, but Mixin matches on the descriptor — spelling it out
 * means a shape change in a future version fails at **build** time instead of silently doing nothing at
 * runtime (the failure mode this project has hit twice, `AGENTS.md` §4.3).
 */
@Mixin(InGameHud.class)
public abstract class InGameHudHealthMixin {

    private static final String RENDER_HEALTH_BAR =
            "renderHealthBar(Lnet/minecraft/client/gui/DrawContext;"
                    + "Lnet/minecraft/entity/player/PlayerEntity;IIIIFIIIZ)V";

    @Inject(method = RENDER_HEALTH_BAR, at = @At("HEAD"), cancellable = true)
    private void simpletweaks$barHealth(
            DrawContext context,
            PlayerEntity player,
            int x,
            int y,
            int lines,
            int regeneratingHeartIndex,
            float maxHealth,
            int lastHealth,
            int health,
            int absorption,
            boolean blinking,
            CallbackInfo ci
    ) {
        // The toggle lives in `/stconfig` (general page). Returning without cancelling leaves vanilla's
        // hearts to draw exactly as they would without this mod — no partial state to clean up.
        if (!dev.firefly.simpletweaks.core.config.GeneralConfig.getBarHealthHud()) {
            return;
        }
        BarHealthHud.INSTANCE.render(
                context, player, x, y, maxHealth, lastHealth, health, absorption, blinking);
        ci.cancel();
    }
}
