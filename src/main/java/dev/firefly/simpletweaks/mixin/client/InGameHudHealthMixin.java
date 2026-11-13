package dev.firefly.simpletweaks.mixin.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Suppresses vanilla hearts only; the bars are drawn by {@code BarsHud}. */
@Mixin(InGameHud.class)
public abstract class InGameHudHealthMixin {

    /** 8th int is the real health, 9th is vanilla's animated value — do not read the 9th. */
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
