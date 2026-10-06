package dev.firefly.simpletweaks.client

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import dev.firefly.simpletweaks.compat.STLog

/**
 * Registers `/enchantinfo` (plus the 1.12.2 aliases `/ei` and `/enchinfo`), which opens
 * [EnchantInfoScreen].
 *
 * <h2>Deliberate deviation from 1.12.2</h2>
 * 1.12.2 implemented this as a **server** command (`CommandBase`) that sent an **empty packet**
 * (`PacketOpenEnchantInfo` — no payload at all) to tell the client to open the screen, because a
 * server-side command had no way to touch the client's screen directly.
 *
 * <p>Here it is a **client command**, exactly like `/stconfig`, so **no packet exists or is needed**.
 * Everything the index shows is client-side rendering information, so a server round-trip would be
 * pure ceremony. This was the author's explicit choice.
 *
 * <p>The command is registered three times rather than using Brigadier redirects, because these are
 * three independent top-level names and a redirect would also make the aliases show up as children of
 * `/enchantinfo` in the command tree, which they were not in 1.12.2.
 */
object EnchantInfoCommand {

    private val NAMES = listOf("enchantinfo", "ei", "enchinfo")

    fun register() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            for (name in NAMES) {
                dispatcher.register(
                    ClientCommandManager.literal(name).executes { context ->
                        STLog.log("EnchantInfo", "command=$name, outcome=screen-queued")
                        ClientScreenOpener.queue(EnchantInfoScreen(context.source.client.currentScreen))
                        1
                    }
                )
            }
        }
    }
}
