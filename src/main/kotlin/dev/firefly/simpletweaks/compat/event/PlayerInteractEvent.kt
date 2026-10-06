package dev.firefly.simpletweaks.compat.event

import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.util.Hand
import net.minecraft.world.World

/**
 * 1.21 replacement for Forge's `PlayerInteractEvent` hierarchy (2 usages).
 *
 * Only `RightClickItem` is provided, bridged from Fabric's `UseItemCallback.EVENT`.
 * It extends [Event] directly (with its own `player`) rather than [PlayerEvent], because Kotlin's
 * single inheritance would otherwise prevent the sealed [PlayerEvent] hierarchy from staying closed.
 *
 * <p><b>Not yet provided: `PlayerInteractEvent.LeftClickEmpty`</b> (`ClientHandler`, an
 * InfinitePower handler). Forge fired it client-side when the player left-clicked with nothing in
 * reach; that is a pure input event with no Fabric API equivalent. It needs a client mixin or an
 * input hook and is deferred to the client batch (phase 6) for the same reason
 * `HarvestDropsEvent` is deferred — a declared-but-never-fired event is worse than none.
 */
sealed class PlayerInteractEvent(
    val player: PlayerEntity,
    val world: World,
    val hand: Hand
) : Event() {

    class RightClickItem(
        player: PlayerEntity,
        world: World,
        hand: Hand,
        val itemStack: ItemStack
    ) : PlayerInteractEvent(player, world, hand) {
        override val isCancelable: Boolean get() = true
    }
}
