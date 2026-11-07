package dev.firefly.simpletweaks.network.packets

import dev.firefly.simpletweaks.SimpleTweaks
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

/**
 * Server -&gt; client: "this entity's level is N".
 *
 * Sent from `EntityTrackingEvents.START_TRACKING`, i.e. **once per player per mob**, at the moment the
 * server starts telling that player about the entity. That covers all three cases that matter without any
 * polling:
 *
 * | case | why it is covered |
 * |---|---|
 * | a mob spawns near a player | the spawn immediately starts tracking |
 * | a player walks into a chunk with existing mobs | tracking starts when they enter range |
 * | a player logs in | same |
 *
 * The level is **always** sent, including `1`/`0`, so that a recycled entity id cannot keep a stale tag
 * from a dead mob. See `MobLevelSync` for why the client cannot derive the level on its own.
 */
class PacketMobLevel(
    /** `Entity#getId`, the same id space `PacketDamageIndicator` uses. */
    val entityId: Int,
    val level: Int,
) : CustomPayload {

    override fun getId(): CustomPayload.Id<PacketMobLevel> = ID

    companion object {

        val ID: CustomPayload.Id<PacketMobLevel> =
            CustomPayload.Id(Identifier.of(SimpleTweaks.MOD_ID, "mob_level"))

        val CODEC: PacketCodec<RegistryByteBuf, PacketMobLevel> = PacketCodec.of(
            { value: PacketMobLevel, buf: RegistryByteBuf ->
                buf.writeVarInt(value.entityId)
                buf.writeVarInt(value.level)
            },
            { buf: RegistryByteBuf ->
                PacketMobLevel(buf.readVarInt(), buf.readVarInt())
            },
        )
    }
}
