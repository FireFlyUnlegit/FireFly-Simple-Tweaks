package dev.firefly.simpletweaks.enchantments.handlers.legendary

import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.compat.WorldSide
import dev.firefly.simpletweaks.compat.event.PlayerDropsEvent
import dev.firefly.simpletweaks.compat.event.PlayerEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.ModEnchantmentKeys
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.ItemEntity
import net.minecraft.item.ItemStack
import java.util.UUID

/**
 * 1.21 port of `enchantments/handlers/legendary/EnchantSoulBoundHandler.kt` (63 lines).
 *
 * Keeps `soul_bound` items out of a player's death drops and gives them back on respawn.
 *
 * Line-by-line mapping from 1.12.2:
 * | 1.12.2                                              | 1.21.1                                                            |
 * |-----------------------------------------------------|-------------------------------------------------------------------|
 * | `net.minecraftforge...PlayerDropsEvent`             | `compat.event.PlayerDropsEvent`                                    |
 * | `e.entityPlayer` / `e.drops`                        | same names                                                         |
 * | `player.world.isRemote`                             | `WorldSide.isClient(player.world)` (field_9236 is a FIELD)          |
 * | `EntityItem` / `drop.item`                          | `ItemEntity` / `drop.stack` (`ItemEntity.getStack`, method_6983)    |
 * | `e.isWasDeath`                                      | `e.wasDeath`                                                       |
 * | `newPlayer.inventory.addItemStackToInventory(s)`    | `e.player.inventory.insertStack(s)` (`PlayerInventory.insertStack`) |
 * | `newPlayer.dropItem(stack, false)`                  | `e.player.dropItem(stack, false)` (same name, 2-arg overload)        |
 * | `EnchantSoulBound` (Enchantment object)             | `ModEnchantmentKeys.SOUL_BOUND` (RegistryKey)                       |
 *
 * ⚠️ **Forced mapping — please review by hand (the only one in this file).**
 * 1.12.2 persisted the saved drops inside Forge's per-entity NBT
 * (`player.getEntityData().setTag("SoulBoundItems", nbtTagList)`), which is how the data survived the
 * death→respawn player swap: Forge copied `getEntityData()` onto the new entity and `onPlayerClone`
 * read it back off `e.original`. 1.21 has no such per-entity tag bag on a player, so the list is held
 * in [saved] keyed by **player UUID** instead.
 *
 * Keying by UUID rather than by entity identity is what makes this equivalent: a respawn keeps the
 * same UUID, so `onPlayerClone` finds the entry written by `onPlayerDrops` without needing Forge's
 * data-copy step at all. The entry is removed after it is handed back.
 *
 * The other half of the same mapping is that the stacks are kept as live `ItemStack` objects rather
 * than round-tripped through `stack.writeToNBT` / `ItemStack(NBTTagCompound)`. 1.12.2 needed the NBT
 * form only because it was writing into a tag bag; nothing else about the behaviour depends on it.
 * Consequence, recorded rather than hidden: a pending soul-bound list does **not** survive a server
 * restart (neither did Forge's in practice for an unloaded player, but this is the honest statement
 * for the port).
 */
object EnchantSoulBoundHandler : Listenable {

    /** Forge tag name kept for traceability against the 1.12.2 source. */
    private const val TAG = "SoulBoundItems"

    /** Pending soul-bound drops, keyed by player UUID (see the class KDoc for why not NBT). */
    private val saved = HashMap<UUID, MutableList<ItemStack>>()

    @SubscribeEvent
    fun onPlayerDrops(e: PlayerDropsEvent) {
        val player = e.entityPlayer
        if (WorldSide.isClient(player.world)) return

        val toRemove = mutableListOf<ItemEntity>()
        val kept = mutableListOf<ItemStack>()

        for (drop in e.drops) {
            val stack = drop.stack
            if (stack.isEmpty) continue
            if (getItemSpecificEnchantLevel(stack, ModEnchantmentKeys.SOUL_BOUND) <= 0) continue

            kept.add(stack.copy())
            toRemove.add(drop)
        }

        if (toRemove.isEmpty()) return

        e.drops.removeAll(toRemove)
        saved[player.uuid] = kept

        STLog.log("SoulBound") {
            "player=${player.name.string}, drops=${e.drops.size}, saved=${kept.size}, " +
                "items=${kept.joinToString(",") { it.item.toString() }}, outcome=withheld-from-drops"
        }
    }

    @SubscribeEvent
    fun onPlayerClone(e: PlayerEvent.Clone) {
        if (!e.wasDeath) return

        val kept = saved.remove(e.original.uuid) ?: return
        if (kept.isEmpty()) return

        val restored = mutableListOf<ItemStack>()
        for (stack in kept) {
            if (stack.isEmpty) continue
            if (!e.player.inventory.insertStack(stack)) {
                e.player.dropItem(stack, false)
            }
            restored.add(stack)
        }

        STLog.log("SoulBound") {
            "player=${e.player.name.string}, original=${e.original.name.string}, " +
                "restored=${restored.size}, outcome=restored-on-respawn"
        }
    }
}
