package dev.firefly.simpletweaks.network.packets

import dev.firefly.simpletweaks.client.ClientManaPoolCache
import dev.firefly.simpletweaks.client.hud.BarHealthHud
import dev.firefly.simpletweaks.client.particle.CelestialRingParticles
import dev.firefly.simpletweaks.util.elements.ElementType
import dev.firefly.simpletweaks.util.mobs.MobLevelSync
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking

/**
 * Client halves of [PacketCelestialRing] and [PacketManaPoolSync], plus the disconnect cleanup both
 * sides of that session state need.
 *
 * The cleanup lives here rather than in a separate "reset everything" function because each entry is
 * session state that arrived over the wire; a new cache added to another packet belongs in this list.
 */
object PacketCelestialReceiver {

    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(PacketCelestialRing.ID) { payload, context ->
            context.client().execute {
                CelestialRingParticles.spawnRing(context.client(), payload.ownerUuid)
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(PacketManaPoolSync.ID) { payload, context ->
            context.client().execute {
                ClientManaPoolCache.update(payload.playerUuid, payload.manaPool)
            }
        }

        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            ClientManaPoolCache.clear()
            // Entity ids are reused across sessions; a stale level would tag the wrong mob.
            MobLevelSync.clear()
            // The health bar's damage ghost must not survive into the next world (a new player starts
            // full, and a trail from the previous session would draw as phantom damage).
            BarHealthHud.reset()
            // Damage-type tags may have changed while we were away.
            ElementType.clearCache()
        }
    }
}
