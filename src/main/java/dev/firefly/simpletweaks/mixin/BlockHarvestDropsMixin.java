package dev.firefly.simpletweaks.mixin;

import dev.firefly.simpletweaks.compat.EventSeams;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

/**
 * Re-creates Forge's {@code BlockEvent.HarvestDropsEvent} seam (1 usage: {@code EnchantAutoSmeltHandler},
 * which needs the tool to smelt drops, award the smelting XP and spend durability).
 *
 * <h2>Why a mixin at all</h2>
 * Fabric's {@code LootTableEvents.MODIFY} is a loot-table <b>construction</b> event
 * ({@code key, tableBuilder, source, registries}) — it has no per-drop context, so it cannot see the
 * tool or the player, and it runs when the table is loaded rather than when a block is broken. It
 * therefore cannot express this behaviour. Buffering the generated drops is the faithful approach.
 *
 * <h2>How the buffering works</h2>
 * <ol>
 *   <li>HEAD of {@code dropStacks(BlockState, World, BlockPos, BlockEntity, Entity, ItemStack)}
 *       ({@code method_9511}) — the only overload carrying both harvester and tool — opens a buffer
 *       when the harvester is a player.</li>
 *   <li>HEAD of each {@code dropStack(...)} overload, cancellable: while a buffer is open the stack is
 *       recorded and the spawn is cancelled. All three overloads are hooked, and because cancelling
 *       the outermost one stops it delegating to the inner ones, exactly one capture happens per
 *       logical drop regardless of which overload {@code dropStacks} calls.</li>
 *   <li>RETURN of {@code dropStacks} — the event fires on the buffered list, then only the surviving
 *       stacks are spawned (through {@code dropStack}, which is safe here because the buffer is
 *       already cleared).</li>
 * </ol>
 * Loot generation itself is untouched: fortune, silk touch and loot tables all still run normally;
 * only the spawn step is deferred.
 */
@Mixin(Block.class)
public abstract class BlockHarvestDropsMixin {

    @Inject(
            method = "dropStacks(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/entity/BlockEntity;Lnet/minecraft/entity/Entity;Lnet/minecraft/item/ItemStack;)V",
            at = @At("HEAD")
    )
    private static void simpletweaks$beginHarvest(BlockState state, World world, BlockPos pos,
                                                  BlockEntity blockEntity, Entity harvester,
                                                  ItemStack tool, CallbackInfo ci) {
        EventSeams.beginHarvest(world, pos, state, harvester);
    }

    @Inject(
            method = "dropStacks(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/entity/BlockEntity;Lnet/minecraft/entity/Entity;Lnet/minecraft/item/ItemStack;)V",
            at = @At("RETURN")
    )
    private static void simpletweaks$finishHarvest(BlockState state, World world, BlockPos pos,
                                                   BlockEntity blockEntity, Entity harvester,
                                                   ItemStack tool, CallbackInfo ci) {
        EventSeams.finishHarvest();
    }

    // --- capture: whichever overload is reached first cancels and records ---

    @Inject(
            method = "dropStack(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/item/ItemStack;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void simpletweaks$captureDrop(World world, BlockPos pos, ItemStack stack,
                                                 CallbackInfo ci) {
        if (EventSeams.captureHarvestDrop(stack)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "dropStack(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Direction;Lnet/minecraft/item/ItemStack;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void simpletweaks$captureDropWithDirection(World world, BlockPos pos,
                                                              Direction direction, ItemStack stack,
                                                              CallbackInfo ci) {
        if (EventSeams.captureHarvestDrop(stack)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "dropStack(Lnet/minecraft/world/World;Ljava/util/function/Supplier;Lnet/minecraft/item/ItemStack;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    @SuppressWarnings("unchecked")
    private static void simpletweaks$captureDropFromSupplier(World world, Supplier<ItemEntity> supplier,
                                                             ItemStack stack, CallbackInfo ci) {
        if (EventSeams.captureHarvestDrop(stack)) {
            ci.cancel();
        }
    }
}
