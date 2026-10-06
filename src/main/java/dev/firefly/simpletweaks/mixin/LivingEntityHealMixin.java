package dev.firefly.simpletweaks.mixin;

import dev.firefly.simpletweaks.compat.EventSeams;
import dev.firefly.simpletweaks.compat.event.LivingHealEvent;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Re-creates Forge's {@code LivingHealEvent} seam (3 usages).
 *
 * <p>Yarn 1.21.1 ground truth: {@code LivingEntity#heal(float): void} = {@code method_6025},
 * official {@code c}. The amount is mutable and that matters:
 * {@code EnchantDelayedRecoveryHandler} does {@code heal.amount -= tickAmount} to stagger
 * regeneration, so the rewritten value must reach vanilla.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityHealMixin {

    @ModifyVariable(
            method = "heal(F)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private float simpletweaks$rewriteHealAmount(float amount) {
        LivingEntity self = (LivingEntity) (Object) this;
        LivingHealEvent event = EventSeams.heal(self, amount);
        return event.isCanceled() ? 0.0f : event.getAmount();
    }

    @Inject(method = "heal(F)V", at = @At("RETURN"))
    private void simpletweaks$clearHeal(float amount, CallbackInfo ci) {
        EventSeams.clearHeal();
    }
}
