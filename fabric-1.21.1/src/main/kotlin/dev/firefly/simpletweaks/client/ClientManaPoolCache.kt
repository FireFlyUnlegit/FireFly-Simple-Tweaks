package dev.firefly.simpletweaks.client

import java.util.UUID

/**
 * Client-side mirror of other players' mana pools, fed by
 * [dev.firefly.simpletweaks.network.packets.PacketManaPoolSync].
 *
 * Ported 1:1 from 1.12.2 `client/ClientManaPoolCache.kt` (18 lines) — a plain `MutableMap` keyed by
 * UUID, no 1.21 API involved, so nothing needed translating.
 *
 * <p>[clear] exists in the original and is called from the client-disconnect hook, so that a
 * reconnect does not show stale mana values for players who are no longer online. In 1.12.2 that hook
 * was Forge's `FMLNetworkEvent.ClientDisconnectionFromServerEvent`; the Fabric equivalent is
 * `ClientPlayConnectionEvents.DISCONNECT`, wired from [dev.firefly.simpletweaks.SimpleTweaksClient].
 */
object ClientManaPoolCache {

    private val cache = mutableMapOf<UUID, Float>()

    fun update(uuid: UUID, value: Float) {
        cache[uuid] = value
    }

    fun get(uuid: UUID): Float = cache[uuid] ?: 0f

    fun clear() {
        cache.clear()
    }
}
