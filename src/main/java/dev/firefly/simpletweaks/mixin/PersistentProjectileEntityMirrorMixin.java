package dev.firefly.simpletweaks.mixin;

import dev.firefly.simpletweaks.enchantments.handlers.mythic.EnchantMirrorHandler;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.util.hit.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets `mirror` catch an incoming projectile at the player instead of taking the hit.
 *
 * <p>Same seam as the piercing injection, higher priority so the catch is decided first: cancelling
 * `onEntityHit` keeps the projectile alive and moving (that method is also what stops it), and the
 * handler has already reversed its velocity by then.
 *
 * <p>The injection always runs on both sides — the client needs the same verdict to predict the
 * arrow's new owner, otherwise the reversed arrow renders bouncing off a wall it never touched.
 */
@Mixin(value = PersistentProjectileEntity.class, priority = 1100)
public abstract class PersistentProjectileEntityMirrorMixin {

    @Inject(method = "onEntityHit", at = @At("HEAD"), cancellable = true)
    private void simpletweaks$mirror(EntityHitResult entityHitResult, CallbackInfo ci) {
        PersistentProjectileEntity arrow = (PersistentProjectileEntity) (Object) this;
        if (EnchantMirrorHandler.handleHit(arrow, entityHitResult)) {
            ci.cancel();
        }
    }
}
