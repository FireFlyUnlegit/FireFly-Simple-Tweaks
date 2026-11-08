package dev.firefly.simpletweaks.mixin.client;

import dev.firefly.simpletweaks.client.hud.ArmorHud;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces vanilla's row of armour icons (and its toughness pips) with {@link ArmorHud}'s bar.
 *
 * <h2>Why the coordinates are recomputed instead of taken from the arguments</h2>
 * `renderArmor` is **static** and receives four ints whose meaning is only knowable from the caller's
 * locals; the caller passes computed values, not named ones. Recomputing the row from
 * `DrawContext#getScaledWindowWidth/Height` is unambiguous and stable: the HUD block is centred, its left
 * edge is `width / 2 - 91`, and the armour row sits one row above the hearts. The extra rows that vanilla
 * shifts for a multi-row health bar cannot happen here, because our health display is always one row; the
 * only thing that can push the armour row up is the **absorption bar**, which occupies exactly one row.
 *
 * <h2>Why the head is cancelled rather than the icons redirected</h2>
 * `renderArmor` draws container, full/half/empty sprites and the toughness pips in one loop. Cancelling at
 * HEAD is the only clean way to remove all of it; leaving the pips would draw toughness twice.
 */
@Mixin(InGameHud.class)
public abstract class InGameHudArmorMixin {

    private static final String RENDER_ARMOR =
            "renderArmor(Lnet/minecraft/client/gui/DrawContext;"
                    + "Lnet/minecraft/entity/player/PlayerEntity;IIII)V";

    @Inject(method = RENDER_ARMOR, at = @At("HEAD"), cancellable = true)
    private static void simpletweaks$barArmor(
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
        if (player.getArmor() <= 0) {
            return;
        }

        final int healthRowY = context.getScaledWindowHeight() - 39 - dev.firefly.simpletweaks.client.hud.HudBar.LIFT;
        // Absorption occupies the row directly above health, so armour moves up one more row when it is
        // present — the same stacking vanilla does, expressed once.
        final int armorRowY = healthRowY - (player.getAbsorptionAmount() > 0f ? 20 : 10);
        final int leftX = context.getScaledWindowWidth() / 2 - 91;

        ArmorHud.INSTANCE.render(context, player, leftX, armorRowY);
        ci.cancel();
    }
}
