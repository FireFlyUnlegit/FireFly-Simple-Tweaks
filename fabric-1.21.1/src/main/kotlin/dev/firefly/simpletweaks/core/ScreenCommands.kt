package dev.firefly.simpletweaks.core

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.network.NetworkManager
import dev.firefly.simpletweaks.network.packets.PacketOpenModScreen
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource

/**
 * The screen-opening subcommands — `stconfig`, and `enchantinfo` / `ei` / `enchinfo` — as **server**
 * commands.
 *
 * <h2>These used to be client commands, and that is what broke the command tree</h2>
 * A name registered with `ClientCommandRegistrationCallback` is owned by a client-only dispatcher
 * that is consulted **before** the server's tree, and only `dispatcherUnknownCommand` /
 * `dispatcherParseException` are allowed to fall through to the server
 * (`ClientCommandInternals#executeCommand` and `#isIgnoredException`, which carries a `TODO`
 * acknowledging the gap). A shared root is therefore not merely inelegant, it is unreachable:
 * `/simple_tweaks enchant ...` matched the client's `simple_tweaks` node, failed on the unknown
 * `enchant` child with `dispatcherUnknownArgument`, and was reported to the player **without ever
 * being sent to the server**. Only a bare `/fst` got through, because that one *is*
 * `dispatcherUnknownCommand`.
 *
 * <p>So no command is registered client-side any more — the whole tree is server-side, exactly like
 * 1.12.2, and a screen that only exists on the client is opened by [PacketOpenModScreen]. That also
 * brings back 1.12.2's payload-less `PacketOpenEnchantInfo`, which the port had cut.
 *
 * <p>Neither subcommand carries a `requires`: they only open a screen on the player's own client, so
 * asking for operator rights would be theatre. The enchant subcommand, which edits items, keeps its
 * permission check.
 */
object ScreenCommands {

    private val CONFIG_NAMES = listOf("stconfig")
    private val INDEX_NAMES = listOf("enchantinfo", "ei", "enchinfo")

    /** Attaches all four names to [root] and returns it, so the caller can keep chaining. */
    fun attach(root: LiteralArgumentBuilder<ServerCommandSource>): LiteralArgumentBuilder<ServerCommandSource> {
        for (name in CONFIG_NAMES) root.then(screen(name, PacketOpenModScreen.CONFIG))
        for (name in INDEX_NAMES) root.then(screen(name, PacketOpenModScreen.ENCHANT_INDEX))
        return root
    }

    private fun screen(name: String, screen: Int): LiteralArgumentBuilder<ServerCommandSource> =
        CommandManager.literal(name).executes { ctx ->
            // Null from a command block or the console: nothing to send, and nothing to report either.
            val player = ctx.source.player
            if (player != null) {
                NetworkManager.sendToClient(PacketOpenModScreen(screen), player)
                STLog.log("Screen") {
                    "player=${player.name.string}, command=$name, screen=$screen, outcome=packet-sent"
                }
            }
            1
        }
}
