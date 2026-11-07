package dev.firefly.simpletweaks.mixin;

import dev.firefly.simpletweaks.core.config.GeneralConfig;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * `GeneralConfig.noHurtCam` — while enabled, taking damage no longer tilts the camera.
 *
 * <h2>Target, and why it is the whole feature</h2>
 * {@code GameRenderer#tiltViewWhenHurt(MatrixStack, float)} was read out of the Yarn-mapped jar and is
 * **the only place that turns the view from {@code LivingEntity#hurtTime}**: it takes the camera entity,
 * computes {@code hurtTime - tickDelta}, and rotates the stack around {@code RotationAxis.POSITIVE_Z}
 * by {@code 40 * f / 8000}, with a second branch for the death tilt ({@code deathTime}, capped at 20).
 * Nothing else in {@code GameRenderer} reads {@code hurtTime}.
 *
 * <p>Cancelling at {@code HEAD} therefore removes exactly the shake. Everything a player might expect to
 * stay is on a different path and is untouched:
 * <ul>
 *   <li>the red damage overlay — {@code InGameHud}/{@code renderOverlay} reads {@code hurtTime} for the
 *       tint, not this method;</li>
 *   <li>the hurt sound and the hand swing;</li>
 *   <li>knockback, i-frames and the damage itself — this is a view transform, not gameplay.</li>
 * </ul>
 *
 * <h2>Why it is in the {@code client} list</h2>
 * {@code GameRenderer} does not exist on a dedicated server, and the option is purely visual — listing
 * it under {@code "client"} keeps dedicated servers byte-for-byte vanilla (same reasoning as
 * {@link EnchantmentNameColorMixin}).
 *
 * <h2>Why the config read is a static getter</h2>
 * 1.12.2 exposed its options as public static fields and Java call sites read them directly; this port
 * mirrors them as Kotlin {@code @JvmStatic} properties, which Java reads through the generated
 * {@code getX()} accessor — see {@code GeneralConfig}'s KDoc for the mapping.
 */
@Mixin(GameRenderer.class)
public abstract class NoHurtCamMixin {

    @Inject(
            method = "tiltViewWhenHurt(Lnet/minecraft/client/util/math/MatrixStack;F)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void simpletweaks$noHurtCam(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        if (GeneralConfig.getNoHurtCam()) {
            ci.cancel();
        }
    }
}
