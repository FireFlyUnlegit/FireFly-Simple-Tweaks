package dev.firefly.simpletweaks.network.packets

import dev.firefly.simpletweaks.SimpleTweaks
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import java.util.UUID

/**
 * Server -&gt; client: keep this client's mana-pool cache in sync for a player.
 *
 * <h2>1.12.2 wire format, kept byte for byte</h2>
 * `long most`, `long least`, `float manaPool` — copied from the original `toBytes`/`fromBytes`.
 *
 * <p>1.12.2's client handler called `ClientManaPoolCache.update(uuid, pool)` directly, with no thread
 * hop — and this port does the same, because Fabric invokes play-payload receivers on the client
 * thread already (the same reason `PacketDamageIndicator`'s receiver only needed a comment about it).
 */
class PacketManaPoolSync(val playerUuid: UUID, val manaPool: Float) : CustomPayload {

    override fun getId(): CustomPayload.Id<PacketManaPoolSync> = ID

    companion object {

        val ID: CustomPayload.Id<PacketManaPoolSync> =
            CustomPayload.Id(Identifier.of(SimpleTweaks.MOD_ID, "mana_pool_sync"))

        val CODEC: PacketCodec<RegistryByteBuf, PacketManaPoolSync> = PacketCodec.of(
            { value: PacketManaPoolSync, buf: RegistryByteBuf ->
                buf.writeLong(value.playerUuid.mostSignificantBits)
                buf.writeLong(value.playerUuid.leastSignificantBits)
                buf.writeFloat(value.manaPool)
            },
            { buf: RegistryByteBuf ->
                PacketManaPoolSync(UUID(buf.readLong(), buf.readLong()), buf.readFloat())
            },
        )
    }
}
