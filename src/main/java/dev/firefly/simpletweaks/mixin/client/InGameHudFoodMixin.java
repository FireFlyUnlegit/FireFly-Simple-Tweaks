package dev.firefly.simpletweaks.mixin.client;

import dev.firefly.simpletweaks.client.hud.HungerHud;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces vanilla's row of food icons with {@link HungerHud}'s bar, mirrored to the right.
 *
 * <h2>Why the coordinates are recomputed</h2>
 * `renderFood(DrawContext, PlayerEntity, int, int)` receives two ints whose order is not visible from its
 * descriptor, and the caller passes locals rather than named values. Recomputing from the HUD block is
 * unambiguous: the block is centred and 182 px wide (`width / 2 + 91` is its right edge), and the food row
 * is the same row as the hearts. Our widget is 89 px, so it is anchored by that **right** edge to keep the
 * food reading where vanilla put it.
 *
 * <h2>Why cancel the head</h2>
 * `renderFood` draws the container, the ten food sprites and the hunger-effect overlay (the greenish
 * variant) in one body. Cancelling at HEAD removes all of it; redirecting draws would leave the overlay
 * behind as a second, half-drawn row.
 */
@Mixin(InGameHud.class)
public abstract class InGameHudFoodMixin {

    private static final String RENDER_FOOD =
            "renderFood(Lnet/minecraft/client/gui/DrawContext;"
                    + "Lnet/minecraft/entity/player/PlayerEntity;II)V";

    @Inject(method = RENDER_FOOD, at = @At("HEAD"), cancellable = true)
    private void simpletweaks$barHunger(
            DrawContext context,
            PlayerEntity player,
            int y,
            int x,
            CallbackInfo ci
    ) {
        if (!dev.firefly.simpletweaks.core.config.GeneralConfig.getBarHealthHud()) {
            return;
        }

        final int healthRowY = context.getScaledWindowHeight() - 39 - dev.firefly.simpletweaks.client.hud.HudBar.LIFT;
        final int rightEdge = context.getScaledWindowWidth() / 2 + 91;
        final int blockX = rightEdge - dev.firefly.simpletweaks.client.hud.HudBar.BLOCK;

        HungerHud.INSTANCE.render(context, player, blockX, healthRowY);
        ci.cancel();
    }
}
