package dev.firefly.simpletweaks.network.packets

import dev.firefly.simpletweaks.SimpleTweaks
import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.network.NetworkManager
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking

/**
 * Client half of [PacketHandshake]: compare protocols and say so once.
 *
 * Kept next to the packet instead of in the client initialiser so "who decodes this" is answerable
 * from the packet's own directory (same rule as the other `*Receiver` files here).
 */
object PacketHandshakeReceiver {

    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(PacketHandshake.ID) { payload, _ ->
            if (payload.protocol != NetworkManager.PROTOCOL_VERSION) {
                SimpleTweaks.LOGGER.warn(
                    "{} protocol mismatch: server={} ({}), client={} ({}). Payloads are decoded with the " +
                        "client's own shapes, so features added or changed on the newer side may silently " +
                        "do nothing.",
                    SimpleTweaks.NAME,
                    payload.protocol,
                    payload.version,
                    NetworkManager.PROTOCOL_VERSION,
                    NetworkManager.modVersion(),
                )
            } else {
                FSTLog.log("Network") {
                    "outcome=handshake-ok, protocol=${payload.protocol}, server=${payload.version}, " +
                        "client=${NetworkManager.modVersion()}"
                }
            }
        }
    }
}
