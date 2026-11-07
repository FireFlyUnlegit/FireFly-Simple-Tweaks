package dev.firefly.simpletweaks.compat.event

import net.minecraft.entity.ItemEntity
import net.minecraft.server.network.ServerPlayerEntity

/**
 * 1.21 replacement for Forge's `PlayerDropsEvent` (1 usage: [EnchantSoulBoundHandler]).
 *
 * <h2>Where Forge fired it</h2>
 * From the player's death path, once the death drops had been *captured* as `EntityItem`s but
 * **before they were spawned into the world**. The handler mutates `getDrops()` to exclude items,
 * and whatever remains is spawned. That "mutate the list, then spawn the survivors" contract is the
 * whole point of the event, so it is reproduced here rather than approximated.
 *
 * <h2>Seam</h2>
 * [dev.firefly.simpletweaks.mixin.ServerPlayerDropsMixin] opens a buffer around
 * `ServerPlayerEntity#onDeath` (the 1.21.1 death path calls `PlayerEntity#dropItem` directly — it
 * does **not** go through `PlayerInventory#dropAll`, which ignores the returned entity), and
 * [dev.firefly.simpletweaks.mixin.PlayerEntityDropItemMixin] records each entity
 * `PlayerEntity#dropItem` produces. The event is posted at the end of `onDeath`.
 *
 * <p><b>One structural difference, deliberately accepted:</b> `PlayerEntity#dropItem` does not spawn
 * the `ItemEntity` itself — the caller does — but in 1.21.1 that happens *inside* `onDeath`, after
 * the capture and before this event can fire. So an entry a handler removes is excluded by
 * discarding the entity instead of by never spawning it. Nothing outside the same tick can observe
 * the difference. Recorded here rather than hidden, because it is the one place the seam is not
 * literally "before the spawn".
 *
 * <p>`drops` is a live [MutableList] — the handlers are expected to remove from it, and
 * [dev.firefly.simpletweaks.compat.EventSeams] diffs it against a snapshot to decide what to discard.
 */
class PlayerDropsEvent(
    val entityPlayer: ServerPlayerEntity,
    /** The `EntityItem`s this death produced. Mutable on purpose — see the class KDoc. */
    val drops: MutableList<ItemEntity>,
) : Event()
