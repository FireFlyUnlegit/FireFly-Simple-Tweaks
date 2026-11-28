package dev.firefly.simpletweaks.network.packets

import dev.firefly.simpletweaks.client.ClientScreenOpener
import dev.firefly.simpletweaks.client.EnchantInfoScreen
import dev.firefly.simpletweaks.client.SimpleTweaksConfigScreen
import dev.firefly.simpletweaks.client.StatsScreen
import dev.firefly.simpletweaks.compat.FSTLog
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking

/**
 * Client half of [PacketOpenModScreen]: the server names a screen, this side opens it.
 *
 * The command lives on the server (a client-registered root would shadow the server's children), so a
 * client-only screen needs this message. Unknown ids are ignored rather than throwing, so a server with
 * a newer build cannot disconnect this client over a screen it does not have.
 */
object PacketOpenModScreenReceiver {

    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(PacketOpenModScreen.ID) { payload, context ->
            context.client().execute {
                val parent = context.client().currentScreen
                val opened = when (payload.screen) {
                    PacketOpenModScreen.CONFIG -> {
                        ClientScreenOpener.queue(SimpleTweaksConfigScreen(parent)); true
                    }
                    PacketOpenModScreen.ENCHANT_INDEX -> {
                        ClientScreenOpener.queue(EnchantInfoScreen(parent)); true
                    }
                    PacketOpenModScreen.STATS -> {
                        ClientScreenOpener.queue(StatsScreen(parent)); true
                    }
                    else -> false
                }
                if (opened) FSTLog.log("Screen") { "screen=${payload.screen}, outcome=opening" }
            }
        }
    }
}
