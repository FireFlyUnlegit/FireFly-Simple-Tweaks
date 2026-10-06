package dev.firefly.simpletweaks.enchantments.handlers.mythic.infinitepower

import dev.firefly.simpletweaks.compat.event.PlayerDropsEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.handlers.mythic.EnchantInfinitePowerHandler

/**
 * Items carrying `infinite_power` are not dropped on death.
 *
 * Port of 1.12.2 `infinitepower/SoulBindHandler.kt` (61 lines), which excluded them from
 * `PlayerDropsEvent.drops` and serialised them into the player's entity NBT, replaying them on
 * `PlayerEvent.Clone`.
 *
 * <h2>⚠️ The storage half is implemented differently, and the reason is 1.21-specific</h2>
 * 1.12.2 wrote NBT through `player.getEntityData()` — Forge's persistent per-entity tag. 1.21 has no
 * such public map, so a literal port would need either a `readCustomDataFromNbt`/`writeCustomDataToNbt`
 * mixin or a Fabric data attachment, i.e. a new persistence seam.
 *
 * <p>It does not need one. `PlayerDropsEvent` fires **after** vanilla has finished dropping the
 * inventory but **before** the entity is discarded, and `ServerPlayerEntity#copyFrom` carries the
 * inventory into the respawned player. So putting the stack straight back into `inventory` is enough:
 * the item survives death by never leaving the inventory, and no serialisation is involved — strictly
 * less machinery than 1.12.2 used.
 *
 * <p>`drops` is a live mutable list and the seam diffs it against a snapshot to decide what to
 * discard, so removing an entry here is the supported way to suppress a drop — see
 * [dev.firefly.simpletweaks.compat.EventSeams.finishPlayerDrops].
 */
object SoulBindHandler : Listenable {

    @SubscribeEvent
    fun onPlayerDrops(e: PlayerDropsEvent) {
        if (e.entityPlayer.world.isClient) return

        val iterator = e.drops.iterator()
        while (iterator.hasNext()) {
            val drop = iterator.next()
            val stack = drop.stack
            if (stack.isEmpty || !EnchantInfinitePowerHandler.hasPower(stack)) continue
            if (e.entityPlayer.inventory.insertStack(stack)) {
                iterator.remove()
                drop.discard()
            }
        }
    }
}
