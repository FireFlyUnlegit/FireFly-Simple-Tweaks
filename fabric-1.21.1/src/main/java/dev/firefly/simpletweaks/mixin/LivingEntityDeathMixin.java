package dev.firefly.simpletweaks.mixin;

import dev.firefly.simpletweaks.compat.ForgeEventBus;
import dev.firefly.simpletweaks.compat.event.LivingDeathEvent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Re-creates Forge's {@code LivingDeathEvent} seam (4 usages).
 *
 * <p>Yarn 1.21.1 ground truth: {@code LivingEntity#onDeath(DamageSource): void} = {@code method_6078},
 * official {@code a} — hence the fully-qualified descriptor.
 *
 * <p>Cancelling must keep the entity alive, exactly as Forge did: Forge's
 * {@code LivingDeathEvent.setCanceled(true)} left the entity at 1 health. Without restoring health
 * the entity would still be flagged dead and removed on the next tick, so {@code setHealth(1.0f)}
 * is what actually implements the cancellation for handlers such as
 * {@code EnchantDeathProtectionHandler} and {@code EnchantImmortalHandler}.
 *
 * <h2>⚠️ Why {@code ServerPlayerEntity} is a second target</h2>
 * Same trap as {@link EntityDamageMixin}: a Mixin only rewrites the body of the class it targets.
 * The flattened-bytecode audit shows {@code ServerPlayerEntity} declares
 * {@code onDeath(DamageSource)} and <b>never calls {@code super.onDeath}</b> — it sends the death
 * message itself and stops there. So on a dedicated server a player's death dispatched to
 * {@code ServerPlayerEntity#onDeath} and the {@code LivingEntity} injection never ran:
 * {@code LivingDeathEvent} was silently dead for players, which is exactly the entity type
 * {@code EnchantDeathProtectionHandler} and {@code EnchantImmortalHandler} care about.
 *
 * <p>The rest of the hierarchy does delegate: {@code PlayerEntity.onDeath} calls
 * {@code LivingEntity.onDeath}, and {@code WolfEntity} / {@code IronGolemEntity} /
 * {@code RaiderEntity} reach it through their intermediate superclasses. {@code EnderDragonEntity},
 * {@code WitherEntity}, {@code ZombieEntity}, {@code EndermanEntity}, {@code WardenEntity} and
 * {@code HoglinEntity} do not declare {@code onDeath} at all, so they inherit the target directly.
 * No class both is a target and delegates, so nothing fires twice.
 */
@Mixin({LivingEntity.class, ServerPlayerEntity.class})
public abstract class LivingEntityDeathMixin {

    @Inject(
            method = "onDeath(Lnet/minecraft/entity/damage/DamageSource;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void simpletweaks$fireLivingDeath(DamageSource source, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        LivingDeathEvent event = new LivingDeathEvent(self, source);
        ForgeEventBus.post(event);
        if (event.isCanceled()) {
            self.setHealth(1.0f);
            ci.cancel();
        }
    }
}
