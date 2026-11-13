package dev.firefly.simpletweaks.mixin.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Suppresses vanilla armour icons only; the bar is drawn by {@code ArmorHud}. */
@Mixin(InGameHud.class)
public abstract class InGameHudArmorMixin {

    private static final String RENDER_ARMOR =
            "renderArmor(Lnet/minecraft/client/gui/DrawContext;"
                    + "Lnet/minecraft/entity/player/PlayerEntity;IIII)V";

    @Inject(method = RENDER_ARMOR, at = @At("HEAD"), cancellable = true)
    private static void simpletweaks$hideVanillaArmor(
            DrawContext context,
            PlayerEntity player,
            int x,
            int y,
            int lines,
            int armor,
            CallbackInfo ci
    ) {
        if (!dev.firefly.simpletweaks.core.config.GeneralConfig.getBarHealthHud()) {
            return;
        }
        ci.cancel();
    }
}
