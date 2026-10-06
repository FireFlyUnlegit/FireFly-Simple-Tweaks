package dev.firefly.simpletweaks.mixin;

import dev.firefly.simpletweaks.enchantments.handlers.rare.EnchantPiercingArrowHandler;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.util.hit.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives `piercing_arrow` a chance to keep the arrow flying after it hits an entity.
 *
 * <h2>1.12.2 original</h2>
 * `EntityArrowPiercingMixin` injected at the head of `EntityArrow#onHit(RayTraceResult)` and cancelled
 * it when `EnchantPiercingArrowHandler.handleHit(...)` returned true. Because 1.12.2's `onHit` was the
 * single entry point for both block and entity collisions, cancelling it simply meant "do not stop,
 * do not remove".
 *
 * <h2>Why the target is `onEntityHit` and not `onHit`</h2>
 * 1.21 has no single method: `PersistentProjectileEntity` splits the work into
 * `onEntityHit(EntityHitResult)` and `onBlockHit(BlockHitResult)`, and **`onEntityHit` both applies the
 * damage and stops the arrow**. Cancelling it without doing anything else would yield an arrow that
 * passes through a mob while dealing no damage at all, so the handler re-implements the damage before
 * the cancel. See `EnchantPiercingArrowHandler`'s KDoc for the full reasoning.
 *
 * <p>Block hits need no handling: they go to `onBlockHit`, which this mixin does not touch, so an
 * arrow that misses everything still sticks in the ground exactly as before — the same as 1.12.2,
 * where `handleHit` returned false for a `RayTraceResult` with no entity.
 *
 * <p><b>Override audit:</b> `onEntityHit` is declared by `PersistentProjectileEntity` and not
 * overridden by `ArrowEntity`, `SpectralArrowEntity` or `TridentEntity` in the vanilla jar, so this
 * single target covers every projectile the mod's bow enchantments can produce.
 */
@Mixin(PersistentProjectileEntity.class)
public abstract class PersistentProjectileEntityPiercingMixin {

    @Inject(method = "onEntityHit", at = @At("HEAD"), cancellable = true)
    private void simpletweaks$piercing(EntityHitResult entityHitResult, CallbackInfo ci) {
        PersistentProjectileEntity arrow = (PersistentProjectileEntity) (Object) this;
        if (EnchantPiercingArrowHandler.handleHit(arrow, entityHitResult)) {
            ci.cancel();
        }
    }
}
