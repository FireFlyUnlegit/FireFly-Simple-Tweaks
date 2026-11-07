package dev.firefly.simpletweaks.network.packets

import dev.firefly.simpletweaks.SimpleTweaks
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

/**
 * Server -> client damage-indicator packet.
 *
 * <h2>1.12.2 vs 1.21.1 wire format</h2>
 * 1.12.2's `PacketDamageIndicator.toBytes` wrote nine fields:
 * <pre>
 *   int entityId, float actualDamage, float originalDamage,
 *   boolean isHeal, boolean isOverkill, boolean isSelf,
 *   float maxHealth, float realMaxHealth, float afterRealHealth
 * </pre>
 * This port writes the same shape **minus `originalDamage` and `isOverkill`** (`build=cleanup47`), plus
 * the element and role bytes. See the "Why there is no original damage any more" section for why the two
 * fields were deleted rather than kept on the wire.
 *
 * <h2>Why there is no "original damage" any more</h2>
 * The number was a pre-clamp figure recorded on `LivingDamageEvent` - i.e. the damage **before** it was
 * capped by the target's remaining health - and was rendered as `original(actual)`. In practice the two
 * disagreed so violently that the display was actively misleading: a `heavenly_punishment` proc recorded
 * `damage=150.1` against a 48 HP mob, and the player reasonably read "150" while the health bar only fell
 * by 48. The delta the player can actually observe is the only honest number, so the indicator now
 * reports **just that** - the health difference measured at the end of the tick, i.e. after every
 * handler, absorption and clamp has run. The raw pre-clamp figure is still available in the debug log
 * (`[FST-HeavenlyPunishment] damage=..., targetHealth=...`), where it belongs.
 *
 * <h2>What replaced Forge's `IMessage` / `IMessageHandler`</h2>
 * 1.12.2 split each packet into an `IMessage` (the fields + `toBytes`/`fromBytes`) and a nested
 * `IMessageHandler` whose `onMessage` ran on the network thread and had to hop to the client thread
 * via `Minecraft.getMinecraft().addScheduledTask { ... }`. 1.21/Fabric replaces both with:
 * <ul>
 *   <li>this class implementing [CustomPayload] - the fields plus an [Identifier];</li>
 *   <li>[CODEC] - the equivalent of `toBytes`/`fromBytes`, as one composable [PacketCodec];</li>
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
    /**
     * Health actually removed, measured at the end of the tick.
     *
     * Never larger than the target's health before the hit - that is the whole point of the
     * `cleanup47` change (see the class KDoc, "Why there is no original damage any more").
     */
    val actualDamage: Float,
    val isHeal: Boolean,
    val isSelf: Boolean,
    val maxHealth: Float,
    val realMaxHealth: Float,
    val afterRealHealth: Float,
    /**
     * `ElementType` ordinal of the damage behind this number, or `ElementType.NONE_ORDINAL`.
     *
     * An ordinal rather than `0xRRGGBB` on purpose: the colour lives on the enum (one source of truth),
     * so the wire only has to say *which* element, and a one-byte field cannot drift out of sync with
     * `ElementType.color`.
     *
     * An out-of-range ordinal decodes to `null` in `ElementType.ofOrdinal`, so a stale value falls back
     * to the default colour instead of throwing in the renderer.
     */
    val elementOrdinal: Int,
    /**
     * Which slot of a hit this number is: [ROLE_SINGLE], [ROLE_PHYSICAL] or [ROLE_ELEMENTAL].
     *
     * The client needs this to **place** the number, not just colour it: a split hit sends two packets
     * and they have to land in different parts of the small XZ region around the entity, otherwise they
     * draw on top of each other. A role byte is the minimum information that makes the two placements
     * complementary - both sides derive the same base angle from the entity id, and the role decides
     * which of the two opposite points on it is used (see `DamageNumber`).
     */
    val role: Int,
    /**
     * Translation key of a [ROLE_LABEL] payload, empty for every numeric role.
     *
     * A **key**, not the resolved text: the client translates it in its own language, so a zh_cn client
     * and an en_us client both read the reaction name correctly while the server stays language-agnostic.
     */
    val label: String = "",
) : CustomPayload {

    override fun getId(): CustomPayload.Id<PacketDamageIndicator> = ID

    companion object {

        /** Ordinary number: one merged value for the whole hit. */
        const val ROLE_SINGLE = 0

        /** Physical half of a split hit. */
        const val ROLE_PHYSICAL = 1

        /** Elemental half of a split hit; also the only role without the second health line. */
        const val ROLE_ELEMENTAL = 2

        /**
         * Not a number at all: a **floating label** (today: the reaction name, e.g. 感电 / 融化).
         *
         * A reaction used to be announced on the action bar, which put the text at the bottom of the
         * screen, far from the hit it describes. It is now a `DamageNumber` whose [label] is a translation
         * key, so it rises, fades and fades out with the numbers it belongs to (author, `build=cleanup18`).
         */
        const val ROLE_LABEL = 3

        val ID: CustomPayload.Id<PacketDamageIndicator> =
            CustomPayload.Id(Identifier.of(SimpleTweaks.MOD_ID, "damage_indicator"))

        /**
         * Field order follows 1.12.2 `toBytes` with the two deleted fields omitted - see the class KDoc.
         * The booleans are written as single bytes, not packed. The two trailing bytes are this port's
         * addition (`build=cleanup30`, `cleanup47`).
         */
        val CODEC: PacketCodec<RegistryByteBuf, PacketDamageIndicator> = PacketCodec.of(
            { value: PacketDamageIndicator, buf: RegistryByteBuf ->
                buf.writeInt(value.entityId)
                buf.writeFloat(value.actualDamage)
                buf.writeBoolean(value.isHeal)
                buf.writeBoolean(value.isSelf)
                buf.writeFloat(value.maxHealth)
                buf.writeFloat(value.realMaxHealth)
                buf.writeFloat(value.afterRealHealth)
                buf.writeByte(value.elementOrdinal)
                buf.writeByte(value.role)
                buf.writeString(value.label)
            },
            { buf: RegistryByteBuf ->
                PacketDamageIndicator(
                    buf.readInt(),
                    buf.readFloat(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    // `readByte` is signed, which is exactly what NONE_ORDINAL (-1) needs.
                    buf.readByte().toInt(),
                    buf.readByte().toInt(),
                    buf.readString(),
                )
            },
        )
    }
}
