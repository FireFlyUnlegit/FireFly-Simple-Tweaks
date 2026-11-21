package dev.firefly.simpletweaks.network.packets

import dev.firefly.simpletweaks.SimpleTweaks
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

/**
 * Client -&gt; server: flip the enchanting table's advanced mode for the sending player.
 *
 * <p>There is no S2C counterpart: the client keeps its own label state and the server's answer is
 * observable through the cost of the next roll, so a round trip would only add latency.
 */
class PacketAdvancedEnchantMode(val enabled: Boolean) : CustomPayload {

    override fun getId(): CustomPayload.Id<PacketAdvancedEnchantMode> = ID

    companion object {

        val ID: CustomPayload.Id<PacketAdvancedEnchantMode> =
            CustomPayload.Id(Identifier.of(SimpleTweaks.MOD_ID, "advanced_enchant_mode"))

        val CODEC: PacketCodec<RegistryByteBuf, PacketAdvancedEnchantMode> = PacketCodec.of(
            { value: PacketAdvancedEnchantMode, buf: RegistryByteBuf ->
                buf.writeBoolean(value.enabled)
            },
            { buf: RegistryByteBuf ->
                PacketAdvancedEnchantMode(buf.readBoolean())
            },
        )
    }
}
