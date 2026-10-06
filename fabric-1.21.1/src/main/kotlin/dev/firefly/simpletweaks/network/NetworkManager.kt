package dev.firefly.simpletweaks.network

import dev.firefly.simpletweaks.enchantments.handlers.mythic.EnchantInfinitePowerHandler
import dev.firefly.simpletweaks.network.packets.PacketCelestialRing
import dev.firefly.simpletweaks.network.packets.PacketDamageIndicator
import dev.firefly.simpletweaks.network.packets.PacketLaser
import dev.firefly.simpletweaks.network.packets.PacketManaPoolSync
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.packet.CustomPayload
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld

/**
 * 1.21.1 replacement for the 1.12.2 `network/NetworkManager.kt` (75 lines).
 *
 * <h2>What actually had to change</h2>
 * 1.12.2 used Forge's `SimpleNetworkWrapper`, which is a <b>channel + integer discriminator</b> model:
 * one channel (`"simple_tweaks"`), and each packet got the next `discriminator++`. 1.21/Fabric has no
 * discriminators — every packet is a [CustomPayload] with its own [net.minecraft.util.Identifier],
 * registered in a global type registry. So the 1.12.2 table:
 * <pre>
 *   PacketLaser              -> discriminator 0, SERVER
 *   PacketDamageIndicator    -> discriminator 1, CLIENT
 *   PacketCelestialRing      -> discriminator 2, CLIENT
 *   PacketManaPoolSync       -> discriminator 3, CLIENT
 *   PacketOpenEnchantInfo    -> discriminator 4, CLIENT
 * </pre>
 * collapses into "one [CustomPayload.Id] per packet, declared on the packet class itself". The
 * discriminator ordering — which was load-bearing in 1.12.2, because both sides had to agree on it —
 * simply disappears, which is one of the few places where the port is structurally simpler.
 *
 * <h2>Scope of this file right now (phase-6 batch 3)</h2>
 * Only `PacketDamageIndicator` is registered, because it is the only packet whose feature exists in
 * the 1.21.1 port so far. The registrations are deliberately **added one at a time next to their
 * feature**, not stubbed up-front, so that an unregistered name can never look like a working channel:
 * <pre>
 *   PacketDamageIndicator    registered here (batch 3)
 *   PacketCelestialRing      when CelestialBlessing is un-deferred
 *   PacketManaPoolSync       batch 4 (ManaPool tooltip)
 *   PacketOpenEnchantInfo    batch 5 -- CUT, see docs/phase6-client-notes.md §11.3
 *   PacketLaser              InfinitePower -- DEFERRED, see docs/infinite-power-deferred.md
 * </pre>
 *
 * <h2>Why there are no `sendToAll` / `sendToAllAround` helpers</h2>
 * 1.12.2 exposed four send directions. Only two are actually used by the ported code:
 * [sendToClient] here, and the "everyone within 64 blocks" case, which 1.12.2's own
 * `DamageIndicatorHandler` already implements by hand (it collects `EntityPlayerMP` within
 * `grow(64.0)` and calls `sendToClient` per player). Keeping that in the caller means this class does
 * not have to guess at a radius/dimension API — the same reason the 1.12.2 author wrote it that way.
 */
object NetworkManager {

    /** Kept for parity with the 1.12.2 constant; unused as a channel name in 1.21 (see the KDoc). */
    const val CHANNEL = "simple_tweaks"

    fun registerPackets() {
        PayloadTypeRegistry.playS2C().register(PacketDamageIndicator.ID, PacketDamageIndicator.CODEC)
        // CelestialBlessing (`mystery` tier) — see docs/phase6-client-notes.md §22.
        PayloadTypeRegistry.playS2C().register(PacketCelestialRing.ID, PacketCelestialRing.CODEC)
        PayloadTypeRegistry.playS2C().register(PacketManaPoolSync.ID, PacketManaPoolSync.CODEC)
        // InfinitePower laser — the project's FIRST C2S payload. See docs/infinite-power-deferred.md §9.
        PayloadTypeRegistry.playC2S().register(PacketLaser.ID, PacketLaser.CODEC)
    }

    /**
     * Server-side receivers, kept separate from [registerPackets] on purpose: that function declares
     * what **can travel** (the type registry), this one decides what **happens when it arrives**. The
     * client side already splits the same two concerns.
     *
     * <p>The body is dispatched onto the server thread explicitly. `fireLaser` kills entities and
     * spawns particles, both of which must run there; relying on Fabric's "handlers already run on the
     * game thread" guarantee would make correctness depend on an API detail rather than on this code.
     *
     * <p>The shooter comes from [net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.Context],
     * not from the packet — which is why [PacketLaser] carries no player id at all.
     */
    fun registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(PacketLaser.ID) { payload, context ->
            val player = context.player()
            player.server.execute {
                EnchantInfinitePowerHandler.fireLaser(player, payload.direction, player.world)
            }
        }
    }

    /** 1.12.2 `sendToClient(packet, player)` — `SimpleNetworkWrapper#sendTo`. */
    fun sendToClient(packet: CustomPayload, player: ServerPlayerEntity) {
        ServerPlayNetworking.send(player, packet)
    }

    /**
     * 1.12.2 `sendToAllAround(packet, dimension, x, y, z, range)`.
     *
     * The dimension is taken from [world] rather than as a separate `Int`: 1.12.2 needed the numeric
     * id because `TargetPoint` is built from one, while a 1.21 `ServerWorld` *is* the dimension, so
     * passing the world removes a way to pass a mismatched pair.
     */
    fun sendToAllAround(
        packet: CustomPayload,
        world: ServerWorld,
        x: Double, y: Double, z: Double,
        range: Double,
    ) {
        val squared = range * range
        for (player in world.server.playerManager.playerList) {
            if (player.world !== world) continue
            if (player.squaredDistanceTo(x, y, z) > squared) continue
            sendToClient(packet, player)
        }
    }
}
