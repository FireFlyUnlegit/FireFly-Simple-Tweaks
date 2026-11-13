package dev.firefly.simpletweaks.mixin.client;

import dev.firefly.simpletweaks.client.hud.BarsHud;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the bar HUD at the tail of {@code renderMainHud}, which runs in every game mode. */
@Mixin(InGameHud.class)
public abstract class InGameHudBarsMixin {

    private static final String RENDER_MAIN_HUD =
            "renderMainHud(Lnet/minecraft/client/gui/DrawContext;"
                    + "Lnet/minecraft/client/render/RenderTickCounter;)V";

    @Inject(method = RENDER_MAIN_HUD, at = @At("TAIL"))
    private void simpletweaks$drawBars(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        BarsHud.INSTANCE.render(context);
    }
}
