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
 * <h2>The two health parameters are NOT what their names suggest</h2>
 * Bytecode of `renderStatusBars` (1.21.1) passes, in order:
 *
 * <pre>
 *   iload 8  → x          iload 10 → y         iload 14 → lines
 *   iload 16 → regeneratingHeartIndex           fload 11 → maxHealth
 *   iload_3  → ceil(player.getHealth())   ← REAL health
 *   iload 7  → this.renderHealthValue     ← animated display value (~1 s lerp)
 *   iload 12 → absorption                       iload 4  → blinking
 * </pre>
 *
 * The descriptor's eighth int is named `lastHealth` and the ninth `health` in Yarn, but the ninth is the
 * animated value: feeding it to a progress bar makes the bar trail the number for up to a second, which is
 * the reported "血量不变之后才看到血条更新" (`build=cleanup87`). This mixin therefore forwards the **eighth**
 * int as the real health and drops the animated one.
 *
 * <h2>Why HEAD + cancel rather than a redirect</h2>
 * `renderHealthBar` draws the container, both rows of hearts, the absorption row, the blink flash and the
 * mount-health fallback in one body; there is no single call to redirect that would remove all of it, and
 * leaving any piece produces a hybrid (bar **and** leftover hearts) that looks broken.
 *
 * <h2>Override audit (`AGENTS.md` §4.3)</h2>
 * The target is `InGameHud`'s own private method — no subclass can override or shadow it, and `InGameHud`
 * has no subclasses in game. `renderMountHealth` is a different method and is deliberately left alone (a
 * ridden mob keeps its vanilla hearts).
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
            int realHealth,
            int animatedHealth,
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
                context, player, x, y, maxHealth, realHealth, absorption, blinking);
        ci.cancel();
    }
}
