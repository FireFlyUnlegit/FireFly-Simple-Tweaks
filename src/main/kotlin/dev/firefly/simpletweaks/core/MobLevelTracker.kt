package dev.firefly.simpletweaks.core

import dev.firefly.simpletweaks.network.NetworkManager
import dev.firefly.simpletweaks.network.packets.PacketMobLevel
import dev.firefly.simpletweaks.util.mobs.MobLevelSync
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents
import net.minecraft.entity.mob.MobEntity

/**
 * Tells each client the level of a mob **at the moment that client starts tracking it**.
 *
 * ## Why this exists
 * `MobLevelMixin` stores the level in a `@Unique` int and writes it to NBT. NBT is server-side: the client's
 * mob is reconstructed from the spawn packet, which never carries that field. So on every client
 * `getMobLevel()` is `0`, and the `[Lv.N]` label logic — display name **and** the `hasLabel` override —
 * both read `0` and did nothing. That is the reported "Nametag 未显示".
 *
 * `EntityTrackingEvents.START_TRACKING` is exactly the right seam: it fires **once per player per entity**,
 * so it covers a fresh spawn, a player walking into a loaded chunk, and a player logging in, without any
 * polling or per-tick cost.
 *
 * ## Why the level is sent even when it is `<= 1`
 * Entity ids are recycled. If only levelled mobs were announced, the id of a dead level-12 zombie would
 * still hold `12` in the client map and the next level-1 mob to receive that id would show a stale tag.
 * Sending every mob unconditionally overwrites it.
 */
object MobLevelTracker {

    fun registerEvents() {
        EntityTrackingEvents.START_TRACKING.register { entity, player ->
            if (entity !is MobEntity) return@register
            val level = MobLevelSync.levelOf(entity)
            NetworkManager.sendToClient(PacketMobLevel(entity.id, level), player)
        }
    }
}
