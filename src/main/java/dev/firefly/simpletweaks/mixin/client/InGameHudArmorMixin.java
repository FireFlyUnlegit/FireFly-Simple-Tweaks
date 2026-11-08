package dev.firefly.simpletweaks.mixin.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses vanilla's armour icons; **the bar itself is drawn by {@code BarHealthHud}**.
 *
 * <h2>Why this mixin is only a suppressor</h2>
 * A modpack that also injects into {@code renderArmor} (InventoryHUD+ does, in the author's client) can
 * cancel at HEAD before this injection runs — and then anything *drawn* here never appears. The author hit
 * exactly that: no armour display at all (`build=cleanup90`). Drawing from the health bar's own pass is
 * safe because this mod replaced that pass itself, so it cannot be pre-empted. This method's only job is to
 * remove the duplicate: if it does not run, the player sees vanilla icons **plus** the bar instead of
 * empty space.
 *
 * <h2>Why the head is cancelled rather than the icons redirected</h2>
 * `renderArmor` draws container, full/half/empty sprites and the toughness pips in one loop; redirecting
 * draws would leave the pips behind as a second, half-drawn row.
 */
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
