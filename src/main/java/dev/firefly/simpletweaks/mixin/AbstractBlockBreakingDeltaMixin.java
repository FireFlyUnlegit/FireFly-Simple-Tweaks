package dev.firefly.simpletweaks.mixin;

import dev.firefly.simpletweaks.compat.EventSeams;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Supplies the block position for the {@code PlayerEvent.BreakSpeed} seam.
 *
 * Yarn 1.21.1 ground truth: {@code AbstractBlock#calcBlockBreakingDelta(BlockState, PlayerEntity,
 * BlockView, BlockPos): float} = {@code method_9594}, official {@code a}.
 *
 * <h2>Why this exists</h2>
 * Break speed is consumed deep inside this method, which — unlike
 * {@code PlayerEntity#getBlockBreakingSpeed(BlockState)} — <b>does</b> receive the
 * {@link BlockPos}. Verified against the bytecode: {@code AbstractBlock} references both
 * {@code getBlockBreakingSpeed} and {@code canHarvest}, while
 * {@code AbstractBlock$AbstractBlockState} only references {@code calcBlockBreakingDelta}, i.e. the
 * 3-arg state-level overload delegates to this 4-arg one. So the position is recorded here and read
 * back by {@link PlayerEntityBreakSpeedMixin} during the synchronous
 * {@code getBlockBreakingSpeed} call inside.
 *
 * <p>This gives {@code BreakSpeed.pos} its exact value instead of an approximation, which matters
 * because {@code EnchantMomentumHandler} uses it to decide whether the player is still mining the
 * same block (an approximation based on the block state would conflate adjacent identical blocks).
 */
@Mixin(AbstractBlock.class)
public abstract class AbstractBlockBreakingDeltaMixin {

    @Inject(
            method = "calcBlockBreakingDelta(Lnet/minecraft/block/BlockState;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;)F",
            at = @At("HEAD")
    )
    private static void simpletweaks$setMiningPos(BlockState state, PlayerEntity player,
                                                  BlockView world, BlockPos pos,
                                                  CallbackInfoReturnable<Float> cir) {
        EventSeams.setMiningPos(pos);
    }

    @Inject(
            method = "calcBlockBreakingDelta(Lnet/minecraft/block/BlockState;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;)F",
            at = @At("RETURN")
    )
    private static void simpletweaks$clearMiningPos(BlockState state, PlayerEntity player,
                                                    BlockView world, BlockPos pos,
                                                    CallbackInfoReturnable<Float> cir) {
        EventSeams.clearMiningPos();
    }
}
