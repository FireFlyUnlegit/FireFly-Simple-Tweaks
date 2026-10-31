package dev.firefly.simpletweaks.util.mobs

import net.minecraft.entity.Entity
import java.util.concurrent.ConcurrentHashMap

/**
 * Client-side mirror of every mob's level, filled by [dev.firefly.simpletweaks.network.packets.PacketMobLevel].
 *
 * ## Why a sync is necessary at all
 * The level lives in a `@Unique` int on `MobEntity` (`MobLevelMixin`) and is written to **NBT**, which is
 * server-side only. A client-created mob is built from its spawn packet, which does not carry that field,
 * so `getMobLevel()` returns `0` on every client. Every client-side reader of the level — the
 * `[Lv.N]` prefix in `EntityDisplayNameMixin` and the `> 1` gate in `LivingEntityRenderNameMixin` — is
 * therefore looking at `0` until this map is filled.
 *
 * `build=cleanup47` needed **two** independent fixes before a single nametag appeared: this sync (the
 * input was missing, `AGENTS.md` §4.11 — 写入成功只完成了一半) and `LivingEntityRenderNameMixin` (its
 * predecessor injected into `EntityRenderer#hasLabel`, which no mob renderer ever executes: the method is
 * overridden by `LivingEntityRenderer` and again by `MobEntityRenderer`).
 *
 * ## Why entity id and not UUID
 * Tracking is rebuilt per player on every chunk (re)load, and the packet is sent again each time (including
 * for unlevelled mobs, so a recycled id is overwritten rather than left stale). Ids are also what
 * `PacketDamageIndicator` already uses, so the wire shape is familiar.
 *
 * The map is intentionally never cleared: entries are bounded by "mobs seen this session" and each one is
 * overwritten by the next `START_TRACKING` for that id. A leak here would be a handful of ints.
 */
object MobLevelSync {

    private val levels = ConcurrentHashMap<Int, Int>()

    /** Called by the client packet receiver. */
    fun put(entityId: Int, level: Int) {
        levels[entityId] = level
    }

    /** Called when leaving a world, so a new session does not inherit stale ids. */
    fun clear() {
        levels.clear()
    }

    /**
     * The level of [entity], from the entity itself on the server and from the synced map on the client.
     *
     * `LevelledMob` is implemented on both sides (the mixin is common), but only the server instance has a
     * meaningful value — the client's is `0` until a packet says otherwise. Preferring the field when it is
     * non-zero keeps the server path exact and needs no round trip.
     */
    fun levelOf(entity: Entity): Int {
        val own = (entity as? LevelledMob)?.mobLevel ?: 0
        if (own > 0) return own
        return levels[entity.id] ?: 0
    }
}
