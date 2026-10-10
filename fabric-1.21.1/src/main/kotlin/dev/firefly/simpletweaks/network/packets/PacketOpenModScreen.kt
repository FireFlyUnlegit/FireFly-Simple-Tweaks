package dev.firefly.simpletweaks.network.packets

import dev.firefly.simpletweaks.SimpleTweaks
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

/**
 * Server -&gt; client: open one of this mod's client-only screens.
 *
 * <h2>Why this packet exists again</h2>
 * 1.12.2 had `PacketOpenEnchantInfo` — **no payload at all**, just "open the index" — and the port
 * deliberately cut it, turning `/enchantinfo` (and `/stconfig`) into *client* commands on the grounds
 * that a client command needs no packet and works even on a server without the mod.
 *
 * <p>That rationale does not survive contact with Fabric's client-command layer. A command name
 * registered **client-side** is resolved against a client-only dispatcher *before* the server's tree
 * is consulted, and only `dispatcherUnknownCommand` / `dispatcherParseException` fall through to the
 * server (`ClientCommandInternals#executeCommand` + `#isIgnoredException`, whose own `TODO` admits it).
 * So a shared root could never reach the server's children: `/simple_tweaks enchant ...` matched the
 * client's `simple_tweaks` node, failed on the unknown `enchant` child with
 * `dispatcherUnknownArgument`, and was swallowed with an error instead of being forwarded.
 *
 * <p>Every command therefore lives in the **server** tree now, which is also what 1.12.2 did. Screens
 * that only exist on the client are opened by asking the client to open them, which is this packet.
 *
 * <h2>Wire format</h2>
 * A single `VarInt` screen id. 1.12.2's packet had no body because there was exactly one screen; the
 * id is what generalises it to two without inventing a second packet. There is no 1.12.2 layout to
 * preserve here, so the shortest correct encoding wins.
 */
class PacketOpenModScreen(val screen: Int) : CustomPayload {

    override fun getId(): CustomPayload.Id<PacketOpenModScreen> = ID

    companion object {

        /** [dev.firefly.simpletweaks.client.SimpleTweaksConfigScreen]. */
        const val CONFIG = 0

        /** [dev.firefly.simpletweaks.client.EnchantInfoScreen]. */
        const val ENCHANT_INDEX = 1

        val ID: CustomPayload.Id<PacketOpenModScreen> =
            CustomPayload.Id(Identifier.of(SimpleTweaks.MOD_ID, "open_mod_screen"))

        val CODEC: PacketCodec<RegistryByteBuf, PacketOpenModScreen> = PacketCodec.of(
            { value: PacketOpenModScreen, buf: RegistryByteBuf ->
                buf.writeVarInt(value.screen)
            },
            { buf: RegistryByteBuf ->
                PacketOpenModScreen(buf.readVarInt())
            },
        )
    }
}
