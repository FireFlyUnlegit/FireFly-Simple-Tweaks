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
 * Suppresses vanilla's ten-heart row; **the bars are drawn by {@code BarsHud}** at the tail of
 * `renderMainHud`.
 *
 * <h2>Why this mixin no longer draws</h2>
 * It used to draw both the health and (later) the armour and hunger bars from inside `renderHealthBar`.
 * That pass does not run in **creative or spectator** mode, so the whole bar HUD vanished there — the
 * reported "护甲值也是有的时候才会显示" (`build=cleanup93`). Drawing moved to a seam that runs in every game
 * mode; this injection is now a pure suppressor, which also means it does nothing harmful if another mod
 * pre-empts it.
 *
 * <h2>The two health arguments are NOT what their names suggest</h2>
 * Bytecode of `renderStatusBars` (1.21.1) passes `ceil(player.getHealth())` as the parameter Yarn names
 * `lastHealth`, and the **animated** `renderHealthValue` as the one named `health`. Reading the latter made
 * the bar trail the number for about a second — the reported "血量不变之后才看到血条更新" (`build=cleanup87`).
 * Since the drawing moved, this file no longer reads either argument; the trap stays documented because the
 * next person to extend this seam will meet it.
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
    private void simpletweaks$hideVanillaHearts(
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
        if (!dev.firefly.simpletweaks.core.config.GeneralConfig.getBarHealthHud()) {
            return;
        }
        ci.cancel();
    }
}
