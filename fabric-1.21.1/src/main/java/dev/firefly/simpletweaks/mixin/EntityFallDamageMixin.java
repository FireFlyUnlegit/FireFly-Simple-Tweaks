package dev.firefly.simpletweaks.mixin;

import dev.firefly.simpletweaks.compat.ForgeEventBus;
import dev.firefly.simpletweaks.compat.event.LivingFallEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Re-creates Forge's {@code LivingFallEvent} seam (1 usage).
 *
 * <p>Yarn 1.21.1 ground truth: {@code handleFallDamage(float, float, DamageSource): boolean} is
 * declared on {@code net/minecraft/entity/Entity} ({@code method_5747}) — <b>not</b> on
 * {@code LivingEntity}, unlike what the name suggests. Hence the {@code instanceof} filter.
 *
 * <p>Returning {@code false} from this method is exactly how vanilla suppresses fall damage, so
 * {@code LivingFallEvent.setCanceled(true)} maps cleanly onto {@code cir.setReturnValue(false)}.
 *
 * <p>Only cancellation is wired; see {@link LivingFallEvent} for why the distance/multiplier
 * fields are read-only.
 */
@Mixin(Entity.class)
public abstract class EntityFallDamageMixin {

    @Inject(
            method = "handleFallDamage(FFLnet/minecraft/entity/damage/DamageSource;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void simpletweaks$fireLivingFall(float fallDistance, float damageMultiplier,
                                             DamageSource source,
                                             CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof LivingEntity living)) {
            return;
        }
        if (self.getWorld().isClient) {
            return;
        }

        LivingFallEvent event = new LivingFallEvent(living, fallDistance, damageMultiplier);
        ForgeEventBus.post(event);
        if (event.isCanceled()) {
            cir.setReturnValue(false);
        }
    }
}
