package dev.firefly.simpletweaks.compat.bridge

import dev.firefly.simpletweaks.compat.ForgeEventBus
import dev.firefly.simpletweaks.compat.event.TickEvent
import dev.firefly.simpletweaks.enchantments.InfinitePowerRainbow
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents

/**
 * Client half of the tick bridge.
 *
 * This class references client-only Fabric API (`ClientTickEvents`) and client-only vanilla types,
 * so it must only ever be loaded on a physical client. It is registered exclusively from
 * [dev.firefly.simpletweaks.SimpleTweaksClient] (`ClientModInitializer`), which fabric-loader does
 * not even instantiate on a dedicated server — that is what keeps the single, un-split source set
 * safe on the server.
 *
 * Signatures verified from the Fabric API sources jar:
 * `ClientTickEvents.START_CLIENT_TICK : onStartTick(MinecraftClient)`,
 * `ClientTickEvents.END_WORLD_TICK : onEndTick(ClientWorld)`.
 *
 * The client player tick is derived from the client world's player list, mirroring
 * [ServerEventBridge] so handlers see identical per-player-per-tick coverage on both sides
 * (Forge fired `PlayerTickEvent` on the client too, and not every handler guards on
 * `e.invalid` — e.g. `EnchantVoidProtectionHandler` only filters on `Phase.END`).
 */
object ClientEventBridge {

    fun register() {
        ClientTickEvents.START_CLIENT_TICK.register { _ ->
            ForgeEventBus.post(TickEvent.ClientTickEvent(TickEvent.Phase.START))
        }
        ClientTickEvents.END_CLIENT_TICK.register { _ ->
            ForgeEventBus.post(TickEvent.ClientTickEvent(TickEvent.Phase.END))
            // 1.12.2 advanced `EnchantInfinitePower.colorTick` from a client tick hook; the 1.21 port
            // keeps the same driver so the rainbow's speed and its "pauses when the game pauses"
            // behaviour are preserved. Placed on END (not START) only because a registration was
            // already here — the original's phase is not recorded either way.
            InfinitePowerRainbow.tick()
        }

        ClientTickEvents.START_WORLD_TICK.register { world ->
            for (player in world.players) {
                ForgeEventBus.post(TickEvent.PlayerTickEvent(TickEvent.Phase.START, player))
            }
        }
        ClientTickEvents.END_WORLD_TICK.register { world ->
            for (player in world.players) {
                ForgeEventBus.post(TickEvent.PlayerTickEvent(TickEvent.Phase.END, player))
            }
        }
    }
}
