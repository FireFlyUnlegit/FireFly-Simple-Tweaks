package dev.firefly.simpletweaks.network.packets

import dev.firefly.simpletweaks.SimpleTweaks
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

/**
 * Server -&gt; client damage-indicator packet.
 *
 * <h2>1.12.2 vs 1.21.1 wire format</h2>
 * The payload is **byte-for-byte the same nine fields in the same order** as 1.12.2's
 * `PacketDamageIndicator.toBytes` / `fromBytes`:
 * <pre>
 *   int entityId, float actualDamage, float originalDamage,
 *   boolean isHeal, boolean isOverkill, boolean isSelf,
 *   float maxHealth, float realMaxHealth, float afterRealHealth
 * </pre>
 * (27 bytes; 1.12.2 wrote them through Netty's `ByteBuf`, 1.21 through `RegistryByteBuf`, which is a
 * `ByteBuf` with registry access — the read/write methods used here are identical.)
 *
 * <h2>What replaced Forge's `IMessage` / `IMessageHandler`</h2>
 * 1.12.2 split each packet into an `IMessage` (the fields + `toBytes`/`fromBytes`) and a nested
 * `IMessageHandler` whose `onMessage` ran on the network thread and had to hop to the client thread
 * via `Minecraft.getMinecraft().addScheduledTask { ... }`. 1.21/Fabric replaces both with:
 * <ul>
 *   <li>this class implementing [CustomPayload] — the fields plus an [Identifier];</li>
 *   <li>[CODEC] — the equivalent of `toBytes`/`fromBytes`, as one composable [PacketCodec];</li>
 *   <li>a receiver registered with `ClientPlayNetworking.registerGlobalReceiver`, which Fabric
 *       already invokes **on the client thread**, so the 1.12.2 `addScheduledTask` hop is no longer
 *       needed. That hop is reproduced in the receiver anyway, with a comment, so the port stays
 *       diffable against the original.</li>
 * </ul>
 *
 * <h2>Why the fields are `val` and the class is not a `data class`</h2>
 * 1.12.2 had a no-arg constructor plus a mutable field set, because Netty instantiated the message
 * and then called `fromBytes`. A codec builds the object in the decoder, so the fields can be
 * immutable and there is no no-arg constructor. A `data class` was avoided only because `equals` /
 * `hashCode` / `toString` are never used on packets here.
 */
class PacketDamageIndicator(
    val entityId: Int,
    val actualDamage: Float,
    val originalDamage: Float,
    val isHeal: Boolean,
    val isOverkill: Boolean,
    val isSelf: Boolean,
    val maxHealth: Float,
    val realMaxHealth: Float,
    val afterRealHealth: Float,
) : CustomPayload {

    override fun getId(): CustomPayload.Id<PacketDamageIndicator> = ID

    companion object {

        val ID: CustomPayload.Id<PacketDamageIndicator> =
            CustomPayload.Id(Identifier.of(SimpleTweaks.MOD_ID, "damage_indicator"))

        /**
         * Field order and primitive types are copied from 1.12.2 `toBytes` — see the class KDoc. The
         * three booleans are written as single bytes, not packed.
         */
        val CODEC: PacketCodec<RegistryByteBuf, PacketDamageIndicator> = PacketCodec.of(
            { value: PacketDamageIndicator, buf: RegistryByteBuf ->
                buf.writeInt(value.entityId)
                buf.writeFloat(value.actualDamage)
                buf.writeFloat(value.originalDamage)
                buf.writeBoolean(value.isHeal)
                buf.writeBoolean(value.isOverkill)
                buf.writeBoolean(value.isSelf)
                buf.writeFloat(value.maxHealth)
                buf.writeFloat(value.realMaxHealth)
                buf.writeFloat(value.afterRealHealth)
            },
            { buf: RegistryByteBuf ->
                PacketDamageIndicator(
                    buf.readInt(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                )
            },
        )
    }
}
