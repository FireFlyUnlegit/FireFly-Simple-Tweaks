package dev.firefly.simpletweaks.mixin;

import dev.firefly.simpletweaks.util.AttributeCaps;
import net.minecraft.entity.attribute.ClampedEntityAttribute;
import net.minecraft.entity.attribute.EntityAttribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Raises (or lowers) the bound of one attribute, without rewriting the attribute registry.
 *
 * <p>Vanilla's cap lives in {@code ClampedEntityAttribute#clamp}, which {@code computeValue} calls as the
 * last step — so even an oversized modifier is squeezed back into {@code [minValue, maxValue]}. This patch
 * always answers from {@link AttributeCaps} instead of the fields, so the ceiling is "unbounded unless the
 * player typed one" rather than "whatever another mod last wrote into the field".
 *
 * <h2>Why not mutate the fields, like AttributeFix does</h2>
 * <p>Writing {@code minValue}/{@code maxValue} once at startup makes the two mods fight over the same
 * field, and the loser depends on load order — a pack that ships AttributeFix would keep its 2147483647
 * ceiling no matter what the config said. Reading the config here instead means the value the player
 * typed always wins, a change made in the GUI applies immediately rather than after a restart, and the
 * fields stay untouched so removing this mod restores vanilla exactly.
 */
@Mixin(ClampedEntityAttribute.class)
public abstract class ClampedEntityAttributeMixin {

    @Inject(method = "clamp", at = @At("HEAD"), cancellable = true)
    private void simpletweaks$clamp(double value, CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(AttributeCaps.clamp((EntityAttribute) (Object) this, value));
    }

    @Inject(method = "getMinValue", at = @At("HEAD"), cancellable = true)
    private void simpletweaks$getMinValue(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(AttributeCaps.minOf((EntityAttribute) (Object) this));
    }

    @Inject(method = "getMaxValue", at = @At("HEAD"), cancellable = true)
    private void simpletweaks$getMaxValue(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(AttributeCaps.maxOf((EntityAttribute) (Object) this));
    }
}
