package dev.firefly.simpletweaks.client

import dev.firefly.simpletweaks.compat.STLog
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback

/**
 * Registers `/stconfig`, which opens [SimpleTweaksConfigScreen].
 *
 * <h2>Why a *client* command</h2>
 * Fabric's client command API runs the command in the client JVM with a `FabricClientCommandSource`,
 * so it can call `MinecraftClient.setScreen` directly. That is why this port needs no packet and no
 * server-side `IGuiHandler` (which 1.12.2 did need, registering one through
 * `NetworkRegistry.INSTANCE.registerGuiHandler`). It also means the command works on any server,
 * including one without the mod's server side — appropriate for something that only changes this
 * client's local config.
 *
 * <p>The screen is opened through [ClientScreenOpener], which defers to the next client tick. Opening
 * it directly from the command callback does **not** work: `ChatScreen` closes itself right after the
 * command runs, discarding the screen. See that class' KDoc and `docs/phase6-client-notes.md` §16.
 */
object ConfigCommand {

    fun register() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                ClientCommandManager.literal("stconfig").executes { context ->
                    // Probe: distinguishes "command never registered" from "screen never opened".
                    STLog.log("Config", "command=stconfig, outcome=screen-queued")
                    ClientScreenOpener.queue(SimpleTweaksConfigScreen(context.source.client.currentScreen))
                    1
                }
            )
        }
    }
}
