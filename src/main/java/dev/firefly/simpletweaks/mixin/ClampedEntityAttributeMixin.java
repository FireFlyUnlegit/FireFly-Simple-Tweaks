package dev.firefly.simpletweaks.mixin;

import dev.firefly.simpletweaks.core.config.AttributeCapConfig;
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
 * consults {@link AttributeCaps} first and answers from the config when that attribute has an override.
 *
 * <h2>Why not mutate the fields, like AttributeFix does</h2>
 * <p>Writing {@code minValue}/{@code maxValue} once at startup makes the two mods fight over the same
 * field, and the loser depends on load order. Reading the config here instead means the value the player
 * typed always wins, and a change made in the GUI applies immediately rather than after a restart. The
 * fields stay untouched, so removing this mod restores vanilla exactly.
 */
@Mixin(ClampedEntityAttribute.class)
public abstract class ClampedEntityAttributeMixin {

    @Inject(method = "clamp", at = @At("HEAD"), cancellable = true)
    private void simpletweaks$clamp(double value, CallbackInfoReturnable<Double> cir) {
        EntityAttribute self = (EntityAttribute) (Object) this;
        AttributeCapConfig.Cap cap = AttributeCaps.capOf(self);
        if (cap != null) {
            cir.setReturnValue(AttributeCaps.clamp(self, value));
        }
    }

    @Inject(method = "getMinValue", at = @At("HEAD"), cancellable = true)
    private void simpletweaks$getMinValue(CallbackInfoReturnable<Double> cir) {
        AttributeCapConfig.Cap cap = AttributeCaps.capOf((EntityAttribute) (Object) this);
        if (cap != null && cap.min != null) {
            cir.setReturnValue(cap.min);
        }
    }

    @Inject(method = "getMaxValue", at = @At("HEAD"), cancellable = true)
    private void simpletweaks$getMaxValue(CallbackInfoReturnable<Double> cir) {
        AttributeCapConfig.Cap cap = AttributeCaps.capOf((EntityAttribute) (Object) this);
        if (cap != null && cap.max != null) {
            cir.setReturnValue(cap.max);
        }
    }
}
