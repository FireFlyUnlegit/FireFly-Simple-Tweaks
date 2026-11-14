package dev.firefly.simpletweaks

import dev.firefly.simpletweaks.compat.bridge.ServerEventBridge
import dev.firefly.simpletweaks.core.EnchantCommand
import dev.firefly.simpletweaks.core.EnchantmentManager
import dev.firefly.simpletweaks.core.config.SimpleTweaksConfig
import dev.firefly.simpletweaks.core.MobLevelTracker
import dev.firefly.simpletweaks.core.MobSpawnEnhancer
import dev.firefly.simpletweaks.core.registerEvents
import dev.firefly.simpletweaks.damageindicator.DamageIndicatorHandler
import dev.firefly.simpletweaks.network.NetworkManager
import dev.firefly.simpletweaks.particle.ModParticles
import dev.firefly.simpletweaks.util.ArrowDamageSeam
import dev.firefly.simpletweaks.util.elements.ElementCombat
import dev.firefly.simpletweaks.util.elements.ElementShredTicker
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
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

        // The unified arrow-damage seam. Registered **before** the handlers on purpose: same-priority
        // listeners dispatch in registration order, so this one runs first within NORMAL and every
        // handler that derives its effect from `e.amount` sees the already-scaled arrow damage.
        // See ArrowDamageSeam's KDoc for why the damage event (and not `onEntityHit`) is the seam.
        ArrowDamageSeam.registerEvents()

        // The elemental engine: one object owns conversion, protection, marks and reactions for all
        // sixteen element enchantments plus mastery (see util/elements/ElementCombat).
        //
        // Its conversion listener sits at `priority = 200`, i.e. **above** LOWEST, so that it runs after
        // every other damage handler and converts the *final* hit rather than a figure that is about to
        // change again. Registration order cannot express that (this object registers before
        // `initHandlers`, so at the same priority it would run first — the opposite of "last"), which is
        // why the number is deliberately out of the five named bands.
        ElementCombat.registerEvents()
        ElementShredTicker.registerEvents()

        // Register every ported enchantment handler on the bus.
        EnchantmentManager.initHandlers()

        // Phase-6 batch 3: the network skeleton plus the damage indicator. `registerPackets` is common
        // (not client-only) because a client must know how to *decode* what a server sends, and on a
        // singleplayer world both sides live in this one JVM.
        // Server: announce each mob's level to a player when that player starts tracking it.
        MobLevelTracker.registerEvents()
        // Server: hand every mob a level at spawn, not only the ones that wear equipment (see its KDoc).
        MobSpawnEnhancer.register()
        NetworkManager.registerPackets()
        NetworkManager.registerServerReceivers()
        DamageIndicatorHandler.registerEvents()

        // The mod's particle types. Registered in common code because `Registries.PARTICLE_TYPE` is a
        // common registry; the sprite/factory half is client-only (see CelestialRingParticles).
        ModParticles.register()

        // `/simple_tweaks enchant ...` -- the unrestricted testing command. Registered in common init
        // (not the client initializer) because it mutates item components, which has to happen on the
        // side that owns the inventory. See EnchantCommand's KDoc for what it bypasses and why the
        // name cannot carry a `modid:` prefix.
        EnchantCommand.register()

        // Damage-type tags can differ between sessions (datapack reload), so the damage-type -> element
        // memo is dropped at every server start. The client half drops it on Join/Disconnect.
        ServerLifecycleEvents.SERVER_STARTING.register { _ ->
            dev.firefly.simpletweaks.util.elements.ElementType.clearCache()
        }

        LOGGER.info("{} load complete.", NAME)
    }

    companion object {
        const val MOD_ID = "simple_tweaks"
        const val NAME = "FireFly's Simple Tweaks"

        /**
         * Bumped from 1.0.7: the 1.21 Fabric build is a new artifact line and does not share
         * saved data with the 1.12.2 version.
         */
        const val VERSION = "2.1.0"

        val LOGGER: Logger = LoggerFactory.getLogger(NAME)
    }
}
