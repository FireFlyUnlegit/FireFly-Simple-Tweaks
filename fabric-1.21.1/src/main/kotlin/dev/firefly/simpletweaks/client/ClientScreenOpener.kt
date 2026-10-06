package dev.firefly.simpletweaks.client

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.gui.screen.Screen

/**
 * Opens a [Screen] on the **next client tick** instead of immediately.
 *
 * <h2>Why this exists</h2>
 * A client command typed into chat is executed synchronously while `ChatScreen` is still finishing its
 * own handling of the Enter key, and `ChatScreen` then closes itself with `client.setScreen(null)`.
 * Opening a screen from inside the command callback therefore produced:
 * <pre>
 *   ChatScreen sends the command
 *     -> our callback calls setScreen(configScreen)
 *   ChatScreen continues and calls setScreen(null)      # the screen is discarded
 * </pre>
 * The screen existed for a fraction of a frame and the player saw nothing, with **no exception in the
 * log** — recorded in `docs/phase6-client-notes.md` §16. `client.execute { }` does not help, because it
 * runs the task immediately when it is already on the client thread, which is the case here.
 *
 * <p>Both `/stconfig` and `/enchantinfo` need this, so it lives in one place.
 */
object ClientScreenOpener {

    private var pending: Screen? = null

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val screen = pending ?: return@register
            pending = null
            client.setScreen(screen)
        }
    }

    fun queue(screen: Screen) {
        pending = screen
    }
}
