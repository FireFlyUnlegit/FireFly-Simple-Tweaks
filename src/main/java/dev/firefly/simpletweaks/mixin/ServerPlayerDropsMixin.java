package dev.firefly.simpletweaks.mixin;

import dev.firefly.simpletweaks.compat.EventSeams;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Buffer window for the {@code PlayerDropsEvent} seam, around {@code ServerPlayerEntity#onDeath}.
 *
 * <h2>Why {@code onDeath} and not {@code dropInventory}</h2>
 * Forge fired {@code PlayerDropsEvent} from the player's death path once the death drops had been
 * captured as {@code EntityItem}s. In 1.21.1 that path is {@code ServerPlayerEntity#onDeath}, which
 * drops the inventory inline via {@code PlayerEntity#dropItem} — it never calls
 * {@code PlayerEntity#dropInventory()} (which {@code ServerPlayerEntity} does not even declare) and
 * it never calls {@code PlayerInventory#dropAll()}. Verified in the bytecode: the only drop-related
 * call inside {@code ServerPlayerEntity#onDeath} is
 * {@code PlayerEntity.dropItem:(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;}.
 *
 * <h2>Why only {@code ServerPlayerEntity}</h2>
 * Drops only exist server-side, and this keeps the buffer off the client entirely. It also means the
 * {@code getWorld().isClient} guard the 1.12.2 handler opens with is satisfied by construction.
 *
 * <h2>Interaction with {@link LivingEntityDeathMixin}</h2>
 * That mixin also injects at the head of {@code onDeath} (for {@code LivingDeathEvent}) and may
 * {@code ci.cancel()} — e.g. {@code EnchantImmortalHandler} keeping a player alive. Cancelling skips
 * the whole body, so no drops are produced and the buffer stays empty; and if {@code ci.cancel()}
 * also skips this mixin's {@code RETURN} hook, the leftover buffer is harmless because
 * {@link EventSeams#beginPlayerDrops} overwrites rather than nests. Nothing leaks across deaths.
 */
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerDropsMixin {

    @Inject(method = "onDeath(Lnet/minecraft/entity/damage/DamageSource;)V", at = @At("HEAD"))
    private void simpletweaks$beginPlayerDrops(DamageSource source, CallbackInfo ci) {
        EventSeams.beginPlayerDrops((ServerPlayerEntity) (Object) this);
    }

    @Inject(method = "onDeath(Lnet/minecraft/entity/damage/DamageSource;)V", at = @At("RETURN"))
    private void simpletweaks$finishPlayerDrops(DamageSource source, CallbackInfo ci) {
        EventSeams.finishPlayerDrops();
    }
}
