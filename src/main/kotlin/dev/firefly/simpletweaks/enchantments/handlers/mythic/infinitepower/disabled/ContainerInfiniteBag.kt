package dev.firefly.simpletweaks.enchantments.handlers.mythic.infinitepower

import dev.firefly.simpletweaks.SimpleTweaks
import dev.firefly.simpletweaks.compat.STLog
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.resource.featuretoggle.FeatureFlags
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.screen.SimpleNamedScreenHandlerFactory
import net.minecraft.screen.slot.Slot
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.collection.DefaultedList

/**
 * The bag's screen handler: [ContainerInfiniteBag.TOTAL_SLOTS] bag slots plus the player's own 36.
 *
 * Port of 1.12.2 `infinitepower/ContainerInfiniteBag.kt` (103 lines), which extended Forge's
 * `Container`.
 *
 * <h2>What the port had to replace</h2>
 * | 1.12.2 | 1.21.1 |
 * |---|---|
 * | `Container` + `addSlotToContainer` | `ScreenHandler` + `addSlot` |
 * | `Slot#getSlotStackLimit` | `Slot#getMaxItemCount` |
 * | anonymous `Slot(inventory, i, 0, 0) { getSlotStackLimit = MAX }` | same shape, `getMaxItemCount()` |
 * | `canInteractWith` | `canUse` |
 * | `transferStackInSlot` | `quickMove` |
 * | `IGuiHandler` + `FMLNetworkHandler.openGui` | a registered `ScreenHandlerType` + `openHandledScreen` |
 *
 * <h2>Two 1.12.2 overrides are deliberately NOT ported</h2>
 * <ol>
 *   <li><b>`mergeItemStack`</b> — 1.12.2 hand-wrote it because Forge's default ignored a custom slot
 *       limit. 1.21's `ScreenHandler#insertItem` already consults `Slot#getMaxItemCount(stack)`
 *       (verified in its bytecode), so with the override below the hand-written version would be a
 *       re-implementation of the same behaviour, i.e. a place for the two to drift apart.</li>
 *   <li><b>`getUsedRows` / `getMaxRows` on the container</b> — they are presentation, and live on
 *       [InfiniteBagInventory] as `usedRows()` / `maxRows()`.</li>
 * </ol>
 *
 * <h2>⚠️ Sync cost, recorded rather than hidden</h2>
 * The handler owns 486 + 36 + 9 = 531 slots. 1.21 syncs a screen handler's slot list as a whole
 * (`ScreenHandler#syncState` builds one `InventoryS2CPacket` over every slot), so each content update
 * is a much larger packet than a vanilla 54-slot chest's. 1.12.2 had the same 486 slots but Forge
 * sent per-slot updates, so this is a genuine regression in update cost for a bag that is nearly
 * empty. It is accepted because the alternative — a virtualised slot list that only exposes the used
 * rows — changes the slot *indices*, and `ContainerInfiniteBag`'s whole contract is that bag slot `i`
 * is bag slot `i` on both sides.
 */
class ContainerInfiniteBag(
    syncId: Int,
    private val playerInventory: PlayerInventory,
    player: PlayerEntity,
) : ScreenHandler(TYPE, syncId) {

    /**
     * Typed as the concrete bag, not as `Inventory`: the client GUI needs `usedRows()` / `maxRows()`
     * to decide the scroll window, and those are bag-specific rather than part of the interface.
     */
    val inventory: InfiniteBagInventory = InfiniteBagInventory(player)

    init {
        // The bag itself. Positions are placeholders: the client GUI repositions the visible window
        // every frame (see GuiInfiniteBag), and off-window slots are parked far off-screen so vanilla
        // never hit-tests them.
        for (i in 0 until TOTAL_SLOTS) {
            addSlot(object : Slot(inventory, i, 0, 0) {
                override fun getMaxItemCount(): Int = Int.MAX_VALUE

                /**
                 * ⚠️ **Both overloads are required.** 1.21 split 1.12.2's single
                 * `getSlotStackLimit()` into two, and the one that matters is this one:
                 * <pre>
                 *   Slot.getMaxItemCount()          -&gt; inventory.getMaxCountPerStack()          // default 64
                 *   Slot.getMaxItemCount(ItemStack) -&gt; min(getMaxItemCount(), stack.getMaxCount())
                 * </pre>
                 * `ScreenHandler#insertItem` calls the **1-arg** form in both its branches (verified
                 * in its bytecode), so overriding only the no-arg form silently caps the bag at the
                 * item's own maximum: a measured 64, with a shift-clicked stack splitting across two
                 * slots once the first hit that ceiling.
                 */
                override fun getMaxItemCount(stack: ItemStack): Int = Int.MAX_VALUE
            })
        }
        // The player's main inventory, then the hotbar — 1.12.2's exact geometry.
        for (row in 0..2) {
            for (col in 0..8) {
                addSlot(Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 139 + row * 18))
            }
        }
        for (col in 0..8) {
            addSlot(Slot(playerInventory, col, 8 + col * 18, 197))
        }

        // The bag's `markDirty` has to reach this handler, or the client is never told that the bag
        // changed. See InfiniteBagInventory#markDirty for what its absence cost.
        inventory.listener = this
    }

    /**
     * Full-state resync, called by the bag inventory whenever its contents change.
     *
     * <h2>Why a full packet rather than the normal delta sync</h2>
     * The normal path is `ScreenHandler#sendContentUpdates`, which compares each slot against
     * `previousTrackedStacks` and sends only the differences. That is correct **provided both sides
     * start from the same contents** — and that proviso is exactly what this container kept failing.
     * A measured session showed the two sides diverging by a constant slot offset: the server's bag is
     * restored from NBT (its low slots already occupied), while the client's starts empty, so an
     * insert lands in client slot 0 and server slot 1. The client then keeps its own placement *and*
     * receives the server's elsewhere, which is why one deposit of 64 appeared as 128, and why taking
     * anything back out failed (the clicked slot is empty server-side, so the prediction is rolled
     * back and the player sees the item vanish).
     *
     * <p>A delta sync can never repair that, because the diverged slot on the client *is* its own
     * phantom: the server never sends slot 0 at all if slot 0 never changed there. Sending the whole
     * slot list after every change makes the client's state a function of the server's, so no phantom
     * can survive a single click. The cost is one large packet per bag edit (531 slots), which is
     * acceptable for a container that is only touched by hand.
     *
     * <p>Server-side only: on the client this returns immediately, which also makes it safe to call
     * from the sync-application path (where `Slot#setStackNoCallbacks` writes through the same
     * `Inventory#setStack` and would otherwise recurse).
     */
    fun resyncBag() {
        val serverPlayer = playerInventory.player as? ServerPlayerEntity ?: return

        val list = DefaultedList.ofSize(slots.size, ItemStack.EMPTY)
        for (i in slots.indices) {
            list[i] = slots[i].stack.copy()
        }
        serverPlayer.networkHandler.sendPacket(
            InventoryS2CPacket(syncId, nextRevision(), list, cursorStack),
        )
    }

    override fun canUse(player: PlayerEntity): Boolean = true

    /**
     * 1.12.2 `transferStackInSlot`: bag slots go to the player, player slots go to the bag.
     *
     * <p>The single log line is deliberate. This seam was reported misbehaving ("shift-click turns one
     * stack into two, and items cannot be taken back out") and the client log contained **nothing** —
     * no exception, no desync warning — because nothing on this path logged. The log's value in that
     * situation is the absence itself, so the probe now exists.
     */
    override fun quickMove(player: PlayerEntity, slotId: Int): ItemStack {
        val slot = slots.getOrNull(slotId) ?: return ItemStack.EMPTY
        if (!slot.hasStack()) return ItemStack.EMPTY

        val stack = slot.stack
        val before = stack.count
        val result = stack.copy()
        val fromBag = slotId < TOTAL_SLOTS

        val moved = if (fromBag) {
            insertItem(stack, TOTAL_SLOTS, slots.size, true)
        } else {
            insertItem(stack, 0, TOTAL_SLOTS, false)
        }

        if (moved) {
            if (stack.isEmpty) {
                slot.stack = ItemStack.EMPTY
            } else {
                slot.markDirty()
            }
        }

        STLog.log("InfinitePower") {
            "bag quickMove: slot=$slotId, from=${if (fromBag) "bag" else "player"}, before=$before, " +
                "remaining=${stack.count}, moved=$moved, outcome=${if (moved) "moved" else "refused"}"
        }
        return if (moved) result else ItemStack.EMPTY
    }

    companion object {

        /** 1.12.2 `TOTAL_SLOTS`. 54 rows of 9 — the bag grows *within* this, it never exceeds it. */
        const val TOTAL_SLOTS = 486

        /** Registered by `SimpleTweaks#onInitialize`; required before any handler is constructed. */
        lateinit var TYPE: ScreenHandlerType<ContainerInfiniteBag>
            private set

        fun register() {
            TYPE = Registry.register(
                Registries.SCREEN_HANDLER,
                Identifier.of(SimpleTweaks.MOD_ID, "infinite_bag"),
                ScreenHandlerType(
                    { syncId, playerInventory -> ContainerInfiniteBag(syncId, playerInventory, playerInventory.player) },
                    FeatureFlags.VANILLA_FEATURES,
                ),
            )
            SimpleTweaks.LOGGER.info("Registered screen handler type: {}", Registries.SCREEN_HANDLER.getId(TYPE))
        }

        /**
         * 1.12.2 `FMLNetworkHandler.openGui(player, modInstance, 0, world, x, y, z)`.
         *
         * Both sides build the handler from the **player** rather than from a payload — the client's
         * `PlayerInventory#player` is the client player, whose bag slots are filled by the vanilla
         * screen-handler sync. That is the same trick 1.12.2's `IGuiHandler` used (it received the
         * player on each side), and it removes the need for an `ExtendedScreenHandlerFactory` and a
         * custom opening payload.
         *
         * <p>The title is a hardcoded literal because 1.12.2's `GuiInfiniteBag` hardcoded the same
         * string in `drawGuiContainerForegroundLayer`; there is no lang key to inherit.
         */
        fun open(player: PlayerEntity) {
            player.openHandledScreen(
                SimpleNamedScreenHandlerFactory(
                    { syncId, playerInventory, p -> ContainerInfiniteBag(syncId, playerInventory, p) },
                    Text.literal("Infinite Bag"),
                )
            )
        }
    }
}
