package dev.firefly.simpletweaks.mixin;

import dev.firefly.simpletweaks.util.SwordBlocking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla shield blocking negates 100%; sword blocking only reduces damage, so it must not match. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityBlockedByShieldMixin {

    @Inject(method = "blockedByShield(Lnet/minecraft/entity/damage/DamageSource;)Z",
            at = @At("HEAD"), cancellable = true)
    private void simpletweaks$disableVanillaShieldBlockForSword(DamageSource source,
                                                                 CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof PlayerEntity player && SwordBlocking.isBlocking(player)) {
            cir.setReturnValue(false);
        }
    }
}
