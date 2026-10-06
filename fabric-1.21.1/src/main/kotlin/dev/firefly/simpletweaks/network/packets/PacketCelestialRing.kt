package dev.firefly.simpletweaks.network.packets

import dev.firefly.simpletweaks.SimpleTweaks
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import java.util.UUID

/**
 * Server -&gt; client: spawn the celestial ring particle effect around a player.
 *
 * <h2>1.12.2 wire format, kept byte for byte</h2>
 * Two `long`s (`mostSignificantBits`, then `leastSignificantBits`) — see the original
 * `toBytes`/`fromBytes`. `writeUuid`/`readUuid` exist in 1.21 and would be shorter, but keeping the
 * two-long form means the packet's layout is unchanged from the original, which is the whole reason
 * the codecs in this package were written by hand.
 *
 * <p>1.12.2 carried the UUID only; the **client** resolved it to a player entity and spawned 8
 * `CelestialRingParticle`s at 45-degree offsets. That stays the client's job — see the receiver in
 * [dev.firefly.simpletweaks.SimpleTweaksClient].
 */
class PacketCelestialRing(val ownerUuid: UUID) : CustomPayload {

    override fun getId(): CustomPayload.Id<PacketCelestialRing> = ID

    companion object {

        val ID: CustomPayload.Id<PacketCelestialRing> =
            CustomPayload.Id(Identifier.of(SimpleTweaks.MOD_ID, "celestial_ring"))

        val CODEC: PacketCodec<RegistryByteBuf, PacketCelestialRing> = PacketCodec.of(
            { value: PacketCelestialRing, buf: RegistryByteBuf ->
                buf.writeLong(value.ownerUuid.mostSignificantBits)
                buf.writeLong(value.ownerUuid.leastSignificantBits)
            },
            { buf: RegistryByteBuf ->
                PacketCelestialRing(UUID(buf.readLong(), buf.readLong()))
            },
        )
    }
}
