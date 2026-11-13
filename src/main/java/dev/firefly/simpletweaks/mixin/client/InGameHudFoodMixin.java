package dev.firefly.simpletweaks.mixin.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Suppresses vanilla food icons only; the bar is drawn by {@code HungerHud}. */
@Mixin(InGameHud.class)
public abstract class InGameHudFoodMixin {

    private static final String RENDER_FOOD =
            "renderFood(Lnet/minecraft/client/gui/DrawContext;"
                    + "Lnet/minecraft/entity/player/PlayerEntity;II)V";

    @Inject(method = RENDER_FOOD, at = @At("HEAD"), cancellable = true)
    private void simpletweaks$hideVanillaFood(
            DrawContext context,
            PlayerEntity player,
            int y,
            int x,
            CallbackInfo ci
    ) {
        if (!dev.firefly.simpletweaks.core.config.GeneralConfig.getBarHealthHud()) {
            return;
        }
        ci.cancel();
    }
}
