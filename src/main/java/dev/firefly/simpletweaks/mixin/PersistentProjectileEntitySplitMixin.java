package dev.firefly.simpletweaks.mixin;

import dev.firefly.simpletweaks.enchantments.handlers.epic.EnchantSplitArrowHandler;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.util.hit.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Splits a split-arrow shot the first time it hits anything.
 *
 * <p>{@code TAIL} is the only safe spot: {@code HEAD} is already taken by the piercing handler, which
 * cancels the method to keep the arrow flying, and a cancelled injection never reaches {@code TAIL}.
 * The piercing handler therefore calls {@code EnchantSplitArrowHandler.onHit} itself; this injection
 * covers every other arrow.
 */
@Mixin(PersistentProjectileEntity.class)
public abstract class PersistentProjectileEntitySplitMixin {

    @Inject(method = "onEntityHit", at = @At("TAIL"))
    private void simpletweaks$split(EntityHitResult entityHitResult, CallbackInfo ci) {
        EnchantSplitArrowHandler.onHit((PersistentProjectileEntity) (Object) this, entityHitResult.getEntity());
    }
}
