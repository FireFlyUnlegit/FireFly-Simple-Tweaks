package dev.firefly.simpletweaks.compat.event

import net.minecraft.block.BlockState
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/**
 * 1.21 replacement for Forge's `BlockEvent` hierarchy.
 *
 * `BreakEvent` is bridged from Fabric's `PlayerBlockBreakEvents.BEFORE` — dropping the event's
 * `xp`/`setExp` members is fine because no 1.12.2 handler touches them
 * (`EnchantTunnelingHandler` only reads `e.pos`).
 *
 * `HarvestDropsEvent` is bridged from a mixin on `Block#dropStacks(BlockState, World, BlockPos,
 * BlockEntity, Entity, ItemStack)` (`method_9511`) — the only drop overload that carries **both** the
 * harvester and the tool, which is exactly what the 1.12.2 event exposed
 * (`EnchantAutoSmeltHandler` needs the harvester to read the tool and cost durability).
 *
 * Drop buffering works by replacing the block's loot generation *output*: vanilla still generates the
 * drops normally, they are collected instead of spawned, the event runs on the collected list, and
 * only the surviving stacks are spawned. Generation itself (fortune, silk touch, loot tables) is
 * therefore untouched.
 *
 * `fortuneLevel` / `silkTouch` are deliberately NOT provided: no ported handler reads them (the
 * 1.12.2 `EnchantAutoSmeltHandler` re-derives silk touch from the tool itself), and computing them
 * would mean resolving vanilla enchantment registry entries for no gain.
 */
sealed class BlockEvent(val world: World, val pos: BlockPos, val state: BlockState) : Event() {

    class BreakEvent(
        world: World,
        pos: BlockPos,
        state: BlockState,
        val player: PlayerEntity
    ) : BlockEvent(world, pos, state) {
        override val isCancelable: Boolean get() = true
    }

    /**
     * Fired once per player-caused block harvest, with the mutable [drops] list.
     * Not cancelable, matching Forge — handlers rewrite [drops] instead.
     */
    class HarvestDropsEvent(
        world: World,
        pos: BlockPos,
        state: BlockState,
        val harvester: PlayerEntity?,
        val drops: MutableList<ItemStack>
    ) : BlockEvent(world, pos, state)
}
