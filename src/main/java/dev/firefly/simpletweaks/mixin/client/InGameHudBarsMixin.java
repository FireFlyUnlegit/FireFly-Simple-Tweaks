package dev.firefly.simpletweaks.mixin.client;

import dev.firefly.simpletweaks.client.hud.BarsHud;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the whole bar HUD at the **tail of `renderMainHud`**.
 *
 * <h2>Why this seam and not the health bar's</h2>
 * `renderMainHud` is the one HUD entry point that runs in **every** game mode and every frame. Drawing from
 * `renderHealthBar` looked natural (this mod already replaces that method) but it disappears in creative and
 * spectator, because vanilla does not render the health bar there — which silently took the armour and
 * hunger bars with it ("护甲值也是有的时候才会显示", `build=cleanup93`).
 *
 * Tail rather than head: at the tail the vanilla hotbar, XP bar and level digits are already on screen, so
 * our rows sit on top of them instead of being overpainted. Nothing of ours is drawn below the survival row,
 * so the overlap is limited to the row the bars occupy.
 *
 * <h2>Suppressors stay separate</h2>
 * `InGameHudHealthMixin` / `InGameHudArmorMixin` / `InGameHudFoodMixin` now only **cancel** the vanilla
 * renderers; they draw nothing. That keeps them safe when another mod injects into the same methods first
 * (InventoryHUD+ does in the author's client), because the visible output no longer depends on them running.
 */
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
