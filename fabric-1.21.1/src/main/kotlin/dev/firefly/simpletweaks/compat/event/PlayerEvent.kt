package dev.firefly.simpletweaks.compat.event

import net.minecraft.block.BlockState
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.BlockPos

/**
 * 1.21 replacement for BOTH of Forge's player events (21 usages):
 *  - `net.minecraftforge.event.entity.player.PlayerEvent`            (has `Clone`)
 *  - `net.minecraftforge.fml.common.gameevent.PlayerEvent`           (has LoggedIn/Out, Respawn,
 *                                                                     ChangedDimension)
 *
 * Having two distinct Forge classes with the same simple name forced the 1.12.2 code to alias one
 * of them (`import ... as Event1` in `EnchantDelayedRecoveryHandler`). Porting both to this single
 * class removes that wart: change both imports to this one and drop the alias.
 *
 * Bridges live in [dev.firefly.simpletweaks.compat.bridge.ServerEventBridge]. The mapping is:
 *  - `PlayerLoggedInEvent`         <- `ServerPlayerEvents.JOIN` (gives the player directly; this is
 *                                     a closer match than `ServerPlayConnectionEvents.JOIN`, which
 *                                     hands over the network handler)
 *  - `PlayerLoggedOutEvent`        <- `ServerPlayerEvents.LEAVE`
 *  - `Clone`                       <- `ServerPlayerEvents.COPY_FROM`
 *  - `PlayerRespawnEvent`          <- `ServerPlayerEvents.AFTER_RESPAWN`
 *  - `PlayerChangedDimensionEvent` <- `ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD`
 */
sealed class PlayerEvent(val player: PlayerEntity) : Event() {

    /**
     * Fired when the player entity is recreated (respawn or dimension change).
     *
     * Forge convention preserved: [player] is the NEW player and [original] is the OLD one. Handlers
     * such as `EnchantVitalityHandler` / `EnchantComboHandler` call `clearModifier(event.original)`
     * to strip attribute modifiers off the discarded entity, so getting this direction right matters.
     * `ServerPlayerEvents.COPY_FROM(oldPlayer, newPlayer, alive)` maps to
     * `Clone(newPlayer, oldPlayer, alive)`.
     */
    class Clone(
        player: PlayerEntity,
        val original: PlayerEntity,
        val wasDeath: Boolean
    ) : PlayerEvent(player)

    class PlayerLoggedInEvent(player: PlayerEntity) : PlayerEvent(player)

    class PlayerLoggedOutEvent(player: PlayerEntity) : PlayerEvent(player)

    /** `endConquered` mirrors Forge: true when respawning through the End portal. */
    class PlayerRespawnEvent(player: PlayerEntity, val endConquered: Boolean) : PlayerEvent(player)

    class PlayerChangedDimensionEvent(player: PlayerEntity) : PlayerEvent(player)

    /**
     * Forge's `PlayerEvent.BreakSpeed` (2 usages: `ToolHandler`, `EnchantMomentumHandler`).
     *
     * Forge modelled this as a `GenericEvent<Float>` exposing a mutable `newSpeed`; here the value
     * is simply a mutable property.
     *
     * Seam: `@ModifyReturnValue` on `PlayerEntity#getBlockBreakingSpeed(BlockState): float`
     * (`method_7351`, official `c`) **and** on `PlayerInventory#getBlockBreakingSpeed(BlockState):
     * float` (official `a`). Both classes declare their own and neither calls the other (verified:
     * the name appears exactly once in each class's constant pool), yet it is not knowable offline
     * which one the mining path uses — so both are hooked, with a HEAD-reset guard in
     * `EventSeams.breakSpeed` so the event still fires exactly once per call chain.
     *
     * **`pos` is supplied exactly, not null.** Forge's `BreakSpeed` carried `getPos()` (the block
     * being mined) and `EnchantMomentumHandler` relies on it to tell "same block" from "new block".
     * `getBlockBreakingSpeed(BlockState)` only receives the state — but the position *is* available
     * one frame up: `AbstractBlock#calcBlockBreakingDelta(BlockState, PlayerEntity, BlockView,
     * BlockPos)` (`method_9594`) receives it and synchronously calls `getBlockBreakingSpeed`.
     * `AbstractBlockBreakingDeltaMixin` records it into a thread-local and
     * `PlayerEntityBreakSpeedMixin` reads it back, so this is the real position rather than an
     * approximation. (Matching on the block state alone would conflate adjacent identical blocks.)
     */
    class BreakSpeed(
        player: PlayerEntity,
        val state: BlockState,
        val pos: BlockPos?,
        var newSpeed: Float
    ) : PlayerEvent(player)

    /**
     * Forge's `PlayerEvent.HarvestCheck` (1 usage: `ToolHandler`'s "always harvestable").
     *
     * Seam: `@ModifyReturnValue` on `PlayerEntity#canHarvest(BlockState): boolean`
     * (official `d`). Note `canHarvestBlock` no longer exists in 1.21 — `canHarvest` is the
     * replacement, and `ServerPlayerInteractionManager` calls it directly.
     */
    class HarvestCheck(
        player: PlayerEntity,
        val state: BlockState,
        var canHarvest: Boolean
    ) : PlayerEvent(player)
}
