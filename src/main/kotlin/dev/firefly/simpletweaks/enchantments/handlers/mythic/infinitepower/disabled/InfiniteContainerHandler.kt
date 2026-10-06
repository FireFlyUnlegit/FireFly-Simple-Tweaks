package dev.firefly.simpletweaks.enchantments.handlers.mythic.infinitepower

import dev.firefly.simpletweaks.compat.WorldSide
import dev.firefly.simpletweaks.compat.event.PlayerEvent
import dev.firefly.simpletweaks.compat.event.PlayerInteractEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.handlers.mythic.EnchantInfinitePowerHandler
import net.minecraft.util.Hand

/**
 * Opens the bag: sneak + right-click while holding an `infinite_power` item.
 *
 * Port of 1.12.2 `infinitepower/InfiniteContainerHandler.kt` (93 lines).
 *
 * | 1.12.2 | 1.21.1 |
 * |---|---|
 * | `PlayerInteractEvent.RightClickItem` | same name in this project's compat layer (bridged from Fabric's `UseItemCallback`) |
 * | `event.entityPlayer`, `world.isRemote` | `event.player`, `WorldSide.isClient(world)` |
 * | `FMLNetworkHandler.openGui(...)` | [ContainerInfiniteBag.open] |
 * | `PlayerEvent.Clone` (`SaveHandler`) | [onClone] here |
 *
 * <h2>⚠️ The second half of the 1.12.2 file is NOT ported</h2>
 * 1.12.2 also had an `onLivingDrops` hook that diverted a killed mob's drops straight into the bag.
 * That needs `LivingDropsEvent`, which this project does not provide — `compat/EventUtils.kt` lists it
 * among the events with no seam. **`DropHandler`'s 64x-multiplier half is gone too**, at the author's
 * request, so no drop interception exists anywhere in the port now. Anything this handler does not do
 * is therefore a missing feature, not a broken one.
 *
 * <h2>Why the clone copy lives here rather than in a `SaveHandler`</h2>
 * 1.12.2 split persistence across `SaveHandler` (on-save flush + clone copy) and
 * `InfiniteBagInventory` (NBT read/write). The on-save half is now the `PlayerEntity` NBT mixin, which
 * leaves only the clone copy — and that one is about the bag, so it belongs next to the bag's opener
 * rather than in a third file whose name would no longer describe its contents.
 *
 * <p>Unlike 1.12.2 (`if (!event.isWasDeath) return`), the entry is copied on **every** clone, not only
 * a death: a dimension change also produces a fresh `ServerPlayerEntity`, and 1.12.2's guard would
 * have silently emptied the bag on every Nether trip.
 */
object InfiniteContainerHandler : Listenable {

    @SubscribeEvent
    fun onRightClickItem(e: PlayerInteractEvent.RightClickItem) {
        val player = e.player
        if (WorldSide.isClient(player.world)) return

        // ⚠️ Without this the bag is opened TWICE per interaction: `RightClickItem` is bridged from
        // Fabric's `UseItemCallback`, which fires once per hand. Two opens means two handlers and two
        // `OpenScreenS2CPacket`s on the server, and the client keeps only the second — while the first
        // handler's inventory still shares the same backing list and keeps resyncing with its own
        // (now stale) syncId. A syncId the client no longer has is dropped silently by
        // `ClientPlayNetworkHandler#onInventory`, which is exactly the symptom that could not be
        // explained from the outside.
        if (e.hand != Hand.MAIN_HAND) return

        val stack = player.mainHandStack
        if (stack.isEmpty) return
        if (!EnchantInfinitePowerHandler.hasPower(stack)) return
        if (!player.isSneaking) return

        ContainerInfiniteBag.open(player)
        e.isCanceled = true
    }

    @SubscribeEvent
    fun onClone(e: PlayerEvent.Clone) {
        if (WorldSide.isClient(e.player.world)) return
        InfiniteBagStore.copy(e.original, e.player)
    }
}
