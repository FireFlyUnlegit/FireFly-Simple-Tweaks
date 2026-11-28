package dev.firefly.simpletweaks.mixin;

import dev.firefly.simpletweaks.compat.ForgeEventBus;
import dev.firefly.simpletweaks.compat.event.PlayerEvent;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Re-creates Forge's {@code PlayerEvent.HarvestCheck} seam (1 usage: {@code ToolHandler}'s
 * "infinite_power harvests everything").
 *
 * <p>Yarn 1.21.1 ground truth: {@code PlayerEntity#canHarvest(BlockState): boolean}, called from
 * {@code AbstractBlock#calcBlockBreakingDelta} — verified with {@code javap} against the mapped jar.
 *
 * <p>This seam was declared and subscribed but never posted, so the listener could not run; the
 * {@code build} check that compares subscribed types against {@code ForgeEventBus.post} call sites is
 * what surfaced it.
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityCanHarvestMixin {

    @Inject(
            method = "canHarvest(Lnet/minecraft/block/BlockState;)Z",
            at = @At("RETURN"),
            cancellable = true
    )
    private void simpletweaks$harvestCheck(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        PlayerEvent.HarvestCheck event = new PlayerEvent.HarvestCheck(
                self, state, cir.getReturnValueZ());
        ForgeEventBus.post(event);
        cir.setReturnValue(event.getCanHarvest());
    }
}
