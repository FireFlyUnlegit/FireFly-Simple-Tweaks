package dev.firefly.simpletweaks

import dev.firefly.simpletweaks.compat.bridge.ServerEventBridge
import dev.firefly.simpletweaks.core.EnchantmentManager
import dev.firefly.simpletweaks.core.config.SimpleTweaksConfig
import dev.firefly.simpletweaks.core.registerEvents
import dev.firefly.simpletweaks.damageindicator.DamageIndicatorHandler
import dev.firefly.simpletweaks.network.NetworkManager
import dev.firefly.simpletweaks.particle.ModParticles
import net.fabricmc.api.ModInitializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 1.21 port of the `@Mod` entry point.
 *
 * 1.12.2 (`@Mod` + `@Mod.EventHandler`):
 *  - `preInit`  -> [onInitialize] (Fabric has a single init phase; registration is split across
 *                  `ModInitializer` for common and `ClientModInitializer` for client-only work)
 *  - `init`     -> nothing needed; the bus is our own and needs no registering
 *  - `onServerStarting` -> `ServerLifecycleEvents.SERVER_STARTING` (phase 7)
 *
 * Declared as a `class` (not a Kotlin `object`) because fabric-loader instantiates entrypoints via
 * a no-argument constructor, which a Kotlin `object` does not expose publicly.
 */
class SimpleTweaks : ModInitializer {

    override fun onInitialize() {
        LOGGER.info("{} v{} loading (Minecraft 1.21.1 / Fabric)...", NAME, VERSION)

        // Config first: anything registered below may read it (the 1.12.2 order had the config
        // annotation system load before preInit handlers ran, for the same reason).
        SimpleTweaksConfig.load()

        // Fabric callbacks -> Forge-shaped events. Must come first: the bridges are what make
        // TickEvent / PlayerEvent reach the handlers registered below.
        ServerEventBridge.register()

        // Register every ported enchantment handler on the bus.
        EnchantmentManager.initHandlers()

        // Phase-6 batch 3: the network skeleton plus the damage indicator. `registerPackets` is common
        // (not client-only) because a client must know how to *decode* what a server sends, and on a
        // singleplayer world both sides live in this one JVM.
        NetworkManager.registerPackets()
        NetworkManager.registerServerReceivers()
        DamageIndicatorHandler.registerEvents()

        // The mod's particle types. Registered in common code because `Registries.PARTICLE_TYPE` is a
        // common registry; the sprite/factory half is client-only (see CelestialRingParticles).
        ModParticles.register()

        LOGGER.info("{} load complete.", NAME)
    }

    companion object {
        const val MOD_ID = "simple_tweaks"
        const val NAME = "FireFly's Simple Tweaks"

        /**
         * Bumped from 1.0.7: the 1.21 Fabric build is a new artifact line and does not share
         * saved data with the 1.12.2 version.
         */
        const val VERSION = "2.0.0"

        val LOGGER: Logger = LoggerFactory.getLogger(NAME)
    }
}
