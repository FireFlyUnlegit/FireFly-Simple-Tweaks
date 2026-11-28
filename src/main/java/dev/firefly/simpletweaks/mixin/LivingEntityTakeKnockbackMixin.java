package dev.firefly.simpletweaks.mixin;

import dev.firefly.simpletweaks.util.SwordBlocking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Scales the knockback strength before generic knockback resistance is applied. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityTakeKnockbackMixin {

    @ModifyVariable(method = "takeKnockback(DDD)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double simpletweaks$reduceBlockingKnockback(double strength) {
        if ((Object) this instanceof PlayerEntity player) {
            return strength * SwordBlocking.knockbackMultiplier(player);
        }
        return strength;
    }
}
