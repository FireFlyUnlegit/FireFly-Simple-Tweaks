package dev.firefly.simpletweaks.mixin;

import dev.firefly.simpletweaks.compat.EventSeams;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Capture half of the {@code PlayerDropsEvent} seam: records every {@code ItemEntity} the player's
 * death produces.
 *
 * <h2>Yarn 1.21.1 ground truth (verified in the bytecode)</h2>
 * <pre>
 *   PlayerEntity#dropItem(ItemStack, boolean, boolean) -&gt; ItemEntity   [declares it, NO super]
 *   ServerPlayerEntity#dropItem(...)                     -&gt; super -&gt; PlayerEntity
 * </pre>
 * Only {@code PlayerEntity} declares the 3-arg overload, so {@code @Mixin(PlayerEntity.class)}
 * covers the whole player chain.
 *
 * <p><b>Two facts that decided the design, both read out of the bytecode rather than assumed:</b>
 * <ol>
 *   <li>{@code PlayerEntity#dropItem} <b>does not spawn</b> the entity — it builds it and returns it
 *       (the method ends {@code aload 6; areturn}). So this RETURN hook sees the entity before it is
 *       spawned, which is exactly Forge's capture point.</li>
 *   <li>{@code ServerPlayerEntity#onDeath} calls {@code PlayerEntity#dropItem} <b>directly</b>. It
 *       does not go through {@code PlayerInventory#dropAll()}, and it would not matter if it did:
 *       {@code dropAll} <b>pops</b> the returned entity. So {@code dropItem} is the only place the
 *       death drops appear.</li>
 * </ol>
 * When no buffer is open (any non-death {@code dropItem} call, e.g. throwing an item) this is a
 * single null check.
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityDropItemMixin {

    @Inject(
            method = "dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;",
            at = @At("RETURN")
    )
    private void simpletweaks$capturePlayerDrop(ItemStack stack, boolean throwRandomly,
                                                boolean retainOwnership,
                                                CallbackInfoReturnable<ItemEntity> cir) {
        EventSeams.capturePlayerDrop(cir.getReturnValue());
    }
}
