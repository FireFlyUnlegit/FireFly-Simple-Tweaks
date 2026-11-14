package dev.firefly.simpletweaks.util.mobs

import dev.firefly.simpletweaks.network.NetworkManager
import dev.firefly.simpletweaks.network.packets.PacketMobLevel
import net.minecraft.entity.Entity
import java.util.UUID
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

    /** Levels already sent to each player this session, keyed `playerUUID -> entity id -> level`. */
    private val announced: MutableMap<UUID, MutableMap<Int, Int>> =
        java.util.Collections.synchronizedMap(java.util.WeakHashMap())

    /** Called by the client packet receiver. */
    fun put(entityId: Int, level: Int) {
        levels[entityId] = level
    }

    /**
     * Server side: sends [level] for [entityId] to [player] unless that player already has exactly that
     * level for that id.
     *
     * Two seams announce the same level — `MobLevelTracker`'s `START_TRACKING` and `MobSpawnEnhancer`'s
     * `ENTITY_LOAD` — and neither is ordered before the other (`build=cleanup24` had to send from both,
     * because a mob added mid-tick starts tracking before it has a level). Their overlap is the **normal**
     * path: a mob entering view is both loaded and tracked. Suppressing the repeat on the server is what
     * keeps a mob farm from paying double packet encoding per spawn.
     *
     * A genuine change (`0 -> 12`, a level-up from `MobLevelProgression`) is **not** a repeat: it differs
     * from what this player was last told and is always sent.
     *
     * The map is weak on players and lives as long as the session's server, which has the same lifetime as
     * the entity-id space it keys.
     */
    fun announce(entityId: Int, level: Int, player: net.minecraft.server.network.ServerPlayerEntity) {
        val playerId = player.uuid
        if (!changed(playerId, entityId, level)) return
        NetworkManager.sendToClient(PacketMobLevel(entityId, level), player)
    }

    /**
     * Server bookkeeping without the packet — returns whether [level] is worth sending to [playerId].
     *
     * Kept separate so `announce` reads as "check, then send", and so a test can drive the bookkeeping
     * without a live connection.
     */
    fun changed(playerId: UUID, entityId: Int, level: Int): Boolean {
        val perPlayer = announced.getOrPut(playerId) { ConcurrentHashMap() }
        return perPlayer.put(entityId, level) != level
    }

    /** Drops the announce bookkeeping for one player (e.g. after a respawn in a new dimension). */
    fun forget(playerId: UUID) {
        announced.remove(playerId)
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
