package dev.firefly.simpletweaks.enchantments.handlers.mythic.infinitepower

import dev.firefly.simpletweaks.compat.STLog
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement
import net.minecraft.nbt.NbtList
import net.minecraft.nbt.NbtOps
import net.minecraft.registry.RegistryOps
import java.util.WeakHashMap

/**
 * Backing store for [InfiniteBagInventory] — the replacement for 1.12.2's
 * `player.getEntityData()` NBT bag.
 *
 * <h2>Why a store rather than reading/writing NBT on every access</h2>
 * 1.12.2's `InfiniteBagInventory(player)` re-read the player's NBT in its constructor and re-wrote it
 * on every `markDirty()`, so it was stateless but did a full item-list parse per operation. 1.21 has
 * no public equivalent of `getEntityData()` at all, so the data has to live *somewhere* between the
 * NBT callbacks — this object is that somewhere.
 *
 * <p>The map is a [WeakHashMap] keyed by the player entity, so an entity that is discarded is
 * collectible and nothing has to prune it. Everything here runs on the server thread (NBT read/write
 * and handler access), which is why no synchronisation is added beyond what the map needs.
 *
 * <h2>Wiring</h2>
 * [dev.firefly.simpletweaks.mixin.PlayerEntityBagDataMixin] injects at the TAIL of
 * `PlayerEntity#writeCustomDataToNbt` / `#readCustomDataFromNbt` and calls [writeToNbt] /
 * [readFromNbt]. Both are declared by `PlayerEntity` itself and are **public** (verified in the
 * bytecode), so the override audit is trivially satisfied — nothing else executes them.
 * [dev.firefly.simpletweaks.enchantments.handlers.mythic.infinitepower.InfiniteContainerHandler]
 * additionally copies the entry across a respawn, because a new `ServerPlayerEntity` instance starts
 * with an empty map.
 */
object InfiniteBagStore {

    /** 1.12.2's key names, kept identical so a world's existing data still reads. */
    private const val ITEMS_KEY = "InfiniteBagItems"
    private const val SIZE_KEY = "InfiniteBagSize"
    private const val SLOT_KEY = "Slot"
    private const val ITEM_KEY = "Item"

    /** 1.12.2 `MAX_ROWS` / `stackLimit`. */
    const val MAX_ROWS = 54
    const val DEFAULT_ROWS = 3

    private val ITEMS = WeakHashMap<PlayerEntity, MutableList<ItemStack>>()

    /** The live item list for [player], creating an empty 3-row bag on first access. */
    @JvmStatic
    fun itemsOf(player: PlayerEntity): MutableList<ItemStack> =
        ITEMS.getOrPut(player) { MutableList(DEFAULT_ROWS * 9) { ItemStack.EMPTY } }

    /** 1.12.2 `ensureCapacity(rows)`. */
    @JvmStatic
    fun ensureCapacity(items: MutableList<ItemStack>, rows: Int) {
        val target = rows * 9
        while (items.size < target) {
            items.add(ItemStack.EMPTY)
        }
    }

    /**
     * Serialise [player]'s bag into [nbt].
     *
     * <p><b>Deviations from 1.12.2's layout.</b> The original wrote
     * `Slot`/`Id`/`Count`/`Damage`/`tag` by hand. 1.21 items are component-based, and an
     * `Id`+`Damage`+`tag` triple cannot represent components, so each stack is written as one
     * `Item` element produced by `ItemStack.CODEC`. `Slot` is kept because the bag is **sparse** —
     * writing only non-empty slots is what makes a 54-row bag cheap when nearly empty, and that
     * property is worth preserving.
     *
     * <p>`RegistryOps` is required, not optional: several component codecs resolve registry entries
     * (enchantments, potions, `attribute_modifiers`), and encoding against plain `NbtOps` silently
     * fails for exactly those items. A per-item failure is skipped rather than thrown, so one bad
     * stack cannot abort a player save.
     */
    @JvmStatic
    fun writeToNbt(player: PlayerEntity, nbt: NbtCompound) {
        val items = ITEMS[player] ?: return
        val ops = RegistryOps.of(NbtOps.INSTANCE, player.world.registryManager)
        val list = NbtList()
        var skipped = 0

        for (i in items.indices) {
            val stack = items[i]
            if (stack.isEmpty) continue
            val encoded = ItemStack.CODEC.encodeStart(ops, stack).result().orElse(null)
            if (encoded == null) {
                skipped++
                continue
            }
            val entry = NbtCompound()
            entry.putInt(SLOT_KEY, i)
            entry.put(ITEM_KEY, encoded)
            list.add(entry)
        }

        nbt.put(ITEMS_KEY, list)
        nbt.putInt(SIZE_KEY, items.size)
        if (skipped > 0) {
            STLog.log("InfinitePower") { "bag save: $skipped stack(s) could not be encoded and were skipped" }
        }
    }

    /** Inverse of [writeToNbt]; an unreadable entry becomes an empty slot rather than an exception. */
    @JvmStatic
    fun readFromNbt(player: PlayerEntity, nbt: NbtCompound) {
        if (!nbt.contains(ITEMS_KEY)) return
        val ops = RegistryOps.of(NbtOps.INSTANCE, player.world.registryManager)
        val list = nbt.getList(ITEMS_KEY, NbtElement.COMPOUND_TYPE.toInt())
        val declared = nbt.getInt(SIZE_KEY)
        val rows = ((declared + 8) / 9).coerceIn(DEFAULT_ROWS, MAX_ROWS)

        val items = MutableList(rows * 9) { ItemStack.EMPTY }
        for (i in 0 until list.size) {
            val entry = list.getCompound(i)
            val slot = entry.getInt(SLOT_KEY)
            if (slot < 0 || slot >= items.size) continue
            val element = entry.get(ITEM_KEY) ?: continue
            items[slot] = ItemStack.CODEC.parse(ops, element).result().orElse(ItemStack.EMPTY)
        }
        ITEMS[player] = items
    }

    /**
     * Carries the bag across a respawn / dimension change.
     *
     * 1.12.2's `SaveHandler.onPlayerClone` did this by copying the NBT tags. Here the new
     * `ServerPlayerEntity` is a different object with a different map key, so the entry itself has to
     * move — the NBT round-trip is unnecessary.
     */
    @JvmStatic
    fun copy(original: PlayerEntity, replacement: PlayerEntity) {
        val items = ITEMS[original] ?: return
        ITEMS[replacement] = ArrayList(items)
    }
}

/**
 * The bag: an unlimited-size, growable player inventory shown by `ContainerInfiniteBag`.
 *
 * Port of 1.12.2 `infinitepower/InfiniteBagInventory.kt` (147 lines), which implemented Forge's
 * `IInventory`. 1.21's [Inventory] is a much smaller interface — `getSizeInventory`, `getName`,
 * `getInventoryStackLimit`, `openInventory`/`closeInventory`, `getFieldCount` and the display-name
 * family all disappeared, and the stack-size cap moved to the **slot**
 * (`Slot#getMaxItemCount`, overridden in `ContainerInfiniteBag`).
 *
 * <p>Persistence is delegated to [InfiniteBagStore]; this class is only the `Inventory` view of it.
 * `markDirty()` therefore has nothing to flush eagerly — the store is written by the NBT callback —
 * and is kept as a no-op-with-a-comment rather than removed, because [Inventory] requires it and
 * because the 1.12.2 version's eager write is exactly what does *not* need reproducing.
 */
class InfiniteBagInventory(val player: PlayerEntity) : Inventory {

    private val items: MutableList<ItemStack> = InfiniteBagStore.itemsOf(player)

    init {
        InfiniteBagStore.ensureCapacity(items, InfiniteBagStore.DEFAULT_ROWS)
    }

    override fun size(): Int = items.size

    override fun isEmpty(): Boolean = items.all { it.isEmpty }

    override fun getStack(slot: Int): ItemStack =
        if (slot in items.indices) items[slot] else ItemStack.EMPTY

    override fun removeStack(slot: Int, amount: Int): ItemStack {
        if (slot !in items.indices) return ItemStack.EMPTY
        val stack = items[slot]
        if (stack.isEmpty) return ItemStack.EMPTY
        val taken = if (stack.count <= amount) {
            val whole = stack.copy()
            items[slot] = ItemStack.EMPTY
            whole
        } else {
            stack.split(amount)
        }
        markDirty()
        STLog.log("InfinitePower") {
            "bag take: slot=$slot, requested=$amount, taken=${taken.count}, " +
                "remaining=${items[slot].count}, outcome=taken"
        }
        return taken
    }

    /**
     * Delegated to the 2-arg form on purpose.
     *
     * 1.12.2 had `removeStackFromSlot` return the stored object directly, and the naive port of that
     * returns a stack the inventory no longer references — correct, but it also means this overload
     * would be a **second, uninstrumented** take path. Since the whole point of the current probes is
     * that "no log line" must mean "the code did not run", every take routes through one place.
     */
    override fun removeStack(slot: Int): ItemStack = removeStack(slot, Int.MAX_VALUE)

    override fun setStack(slot: Int, stack: ItemStack) {
        if (slot >= items.size) {
            InfiniteBagStore.ensureCapacity(items, (slot / 9) + 1)
        }
        val before = items[slot].count
        items[slot] = if (stack.isEmpty) ItemStack.EMPTY else stack.copy()
        markDirty()
        // Logged only on a real change: this is the write path that would expose duplication, and the
        // initial screen sync touches every non-empty slot once.
        if (before != items[slot].count) {
            STLog.log("InfinitePower") {
                "bag setSlot: slot=$slot, before=$before, incoming=${stack.count}, after=${items[slot].count}"
            }
        }
    }

    /**
     * 1.21's `Inventory`-level stack cap, and the first half of the reason the bag was capped at 64.
     *
     * `Slot#getMaxItemCount()` is literally `inventory.getMaxCountPerStack()`, so this has to be
     * unlimited too — overriding only the slot's no-arg overload would leave this default (64) in
     * place for every other path. The **second** half is
     * `Slot#getMaxItemCount(ItemStack) = min(getMaxItemCount(), stack.getMaxCount())`, which is the
     * one `ScreenHandler#insertItem` actually calls; see the slot override in [ContainerInfiniteBag].
     * Both are required.
     */
    override fun getMaxCountPerStack(): Int = Int.MAX_VALUE

    /**
     * The open handler, so [markDirty] can ask it to re-sync — the counterpart of the listener
     * `SimpleInventory` holds for exactly this purpose. Set by [ContainerInfiniteBag].
     */
    var listener: ContainerInfiniteBag? = null

    /**
     * ⚠️ **This is the method that made the bag look like it was losing items.**
     *
     * The first version was an empty method, justified as "the store is flushed by
     * `PlayerEntity#writeCustomDataToNbt`, so there is nothing to write eagerly". That reasoning was
     * correct about **persistence** and wrong about **notification**: `markDirty()` is also how an
     * inventory tells its `ScreenHandler` that something changed, and `ScreenHandler` only sends slot
     * updates when it is told to. With the notification dropped, the server's bag changed silently
     * and the client was never sent anything.
     *
     * <p>The measured consequence, from a real session: the client's bag kept only its own optimistic
     * writes, so the two sides diverged by a constant slot offset (the server's bag is loaded from NBT
     * and its low slots were already occupied, the client's started empty). Inserting four pieces of
     * armour put them in client slots 0/2/4/6 and server slots 1/3/5/7; taking anything back out then
     * failed on the server — the click's target slot was empty there — and the client's prediction was
     * rolled back, which the player sees as the item vanishing. Repeated inserts also *looked* like
     * duplication, because the client kept both its own prediction and the server's separately.
     *
     * <p>**There is still nothing to flush here.** NBT persistence stays on the mixin; this call is
     * purely the sync notification.
     */
    override fun markDirty() {
        listener?.resyncBag()
    }

    override fun canPlayerUse(player: PlayerEntity): Boolean = true

    override fun clear() {
        items.clear()
        InfiniteBagStore.ensureCapacity(items, InfiniteBagStore.DEFAULT_ROWS)
        markDirty()
    }

    /** 1.12.2 `getUsedRows()` — how many rows are worth drawing. */
    fun usedRows(): Int = (size() + 8) / 9

    fun maxRows(): Int = InfiniteBagStore.MAX_ROWS
}
