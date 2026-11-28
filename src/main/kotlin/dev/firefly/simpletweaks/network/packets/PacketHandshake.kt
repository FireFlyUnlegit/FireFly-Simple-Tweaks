package dev.firefly.simpletweaks.network.packets

import dev.firefly.simpletweaks.SimpleTweaks
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

/**
 * Server -> client, sent once when a player joins: "this server speaks protocol N".
 *
 * Every other payload in this package assumes both sides were built from the same source. Nothing
 * enforced that before: a client with an older jar silently decoded a newer payload's bytes (or threw
 * inside the codec, which surfaces only as a disconnect), and the log said nothing either way. This
 * packet turns that into one warning line.
 *
 * It is **advisory**: the client keeps working after a mismatch rather than refusing to play, because
 * the feature that breaks may be one the player never uses.
 */
class PacketHandshake(
    val protocol: Int,
    /** `fabric.mod.json` version of the sending side, for the log line only. */
    val version: String,
) : CustomPayload {

    override fun getId(): CustomPayload.Id<PacketHandshake> = ID

    companion object {

        val ID: CustomPayload.Id<PacketHandshake> =
            CustomPayload.Id(Identifier.of(SimpleTweaks.MOD_ID, "handshake"))

        val CODEC: PacketCodec<RegistryByteBuf, PacketHandshake> = PacketCodec.of(
            { value: PacketHandshake, buf: RegistryByteBuf ->
                buf.writeVarInt(value.protocol)
                buf.writeString(value.version)
            },
            { buf: RegistryByteBuf ->
                PacketHandshake(buf.readVarInt(), buf.readString())
            },
        )
    }
}
