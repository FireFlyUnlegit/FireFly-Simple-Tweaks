package dev.firefly.simpletweaks.network.packets

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.util.mobs.MobLevelSync
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking

/**
 * Client half of [PacketMobLevel].
 *
 * No `execute` hop: the body only writes a `ConcurrentHashMap` (`MobLevelSync`), so a network-thread
 * write is safe. Anything that touched the world would have to hop.
 */
object PacketMobLevelReceiver {

    /** `START_TRACKING` fires once per player per mob; the probe stops after a few lines. */
    private const val LOGGED = 3
    private var logged = 0

    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(PacketMobLevel.ID) { payload, _ ->
            MobLevelSync.put(payload.entityId, payload.level)
            if (logged < LOGGED) {
                logged++
                FSTLog.log("MobLevel") {
                    "outcome=level-synced, entityId=${payload.entityId}, level=${payload.level}"
                }
            }
        }
    }
}
