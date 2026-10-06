package dev.firefly.simpletweaks.mixin;

import dev.firefly.simpletweaks.compat.EventSeams;
import dev.firefly.simpletweaks.compat.ForgeEventBus;
import dev.firefly.simpletweaks.compat.event.PlayerEvent;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Re-creates Forge's {@code PlayerEvent.BreakSpeed} seam (2 usages: {@code ToolHandler},
 * {@code EnchantMomentumHandler}).
 *
 * Yarn 1.21.1 ground truth: {@code PlayerEntity#getBlockBreakingSpeed(BlockState): float} =
 * {@code method_7351}, official {@code c}.
 *
 * <h2>{@code pos} is supplied, not null</h2>
 * Forge's event carried {@code getPos()} (the block being mined) and {@code EnchantMomentumHandler}
 * depends on it to detect "still the same block". {@code getBlockBreakingSpeed} only receives the
 * state, so the position is captured upstream by {@link AbstractBlockBreakingDeltaMixin} into a
 * thread-local and read back here via {@link EventSeams#getMiningPos()}.
 *
 * <p>Only {@code PlayerEntity} is hooked, not {@code PlayerInventory}: the mining path runs
 * {@code AbstractBlockState.calcBlockBreakingDelta} -> {@code AbstractBlock.calcBlockBreakingDelta}
 * -> {@code player.getBlockBreakingSpeed(state)}, so this is the method that actually decides break
 * progress. Hooking the inventory variant as well would risk the event firing twice per tick and
 * double-applying a multiplicative bonus.
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityBreakSpeedMixin {

    @Inject(
            method = "getBlockBreakingSpeed(Lnet/minecraft/block/BlockState;)F",
            at = @At("RETURN"),
            cancellable = true
    )
    private void simpletweaks$breakSpeed(BlockState state, CallbackInfoReturnable<Float> cir) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        PlayerEvent.BreakSpeed event = new PlayerEvent.BreakSpeed(
                self, state, EventSeams.getMiningPos(), cir.getReturnValueF());
        ForgeEventBus.post(event);
        cir.setReturnValue(event.getNewSpeed());
    }
}
