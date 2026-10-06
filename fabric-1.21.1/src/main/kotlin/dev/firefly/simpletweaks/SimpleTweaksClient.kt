package dev.firefly.simpletweaks

import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.compat.bridge.ClientEventBridge
import dev.firefly.simpletweaks.client.AutoSprintHandler
import dev.firefly.simpletweaks.client.ClientManaPoolCache
import dev.firefly.simpletweaks.client.ClientScreenOpener
import dev.firefly.simpletweaks.client.ConfigCommand
import dev.firefly.simpletweaks.client.EnchantInfoCommand
import dev.firefly.simpletweaks.client.InfinitePowerLaserClient
import dev.firefly.simpletweaks.client.particle.CelestialRingParticles
import dev.firefly.simpletweaks.client.tooltips.ManaPoolToolTipHandler
import dev.firefly.simpletweaks.core.config.DamageIndicatorConfig
import dev.firefly.simpletweaks.core.registerEvents
import dev.firefly.simpletweaks.damageindicator.DamageIndicatorManager
import dev.firefly.simpletweaks.damageindicator.DamageIndicatorRenderer
import dev.firefly.simpletweaks.network.packets.PacketCelestialRing
import dev.firefly.simpletweaks.network.packets.PacketDamageIndicator
import dev.firefly.simpletweaks.network.packets.PacketManaPoolSync
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking

/** One-shot guard so the receive-side probe does not flood the log in normal play. */
private var damagePacketLogged = false

/**
 * Client-only entrypoint. Fabric splits initialisation into common ([SimpleTweaks]) and client
 * ([SimpleTweaksClient]) instead of Forge's `@SideOnly` + `FMLPreInitializationEvent` single hook.
 *
 * Everything reached from here may safely touch client-only classes, because fabric-loader never
 * loads this class on a dedicated server.
 */
class SimpleTweaksClient : ClientModInitializer {

    override fun onInitializeClient() {
        SimpleTweaks.LOGGER.info("{} initialising client tick bridge...", SimpleTweaks.NAME)
        ClientEventBridge.register()

        registerDamageIndicatorReceiver()
        DamageIndicatorRenderer.register()

        // `/stconfig` -> in-game config screen, `/enchantinfo` -> enchantment index. Both are client
        // commands; ClientScreenOpener is what actually puts the screen up, on the next tick.
        ClientScreenOpener.register()
        ConfigCommand.register()
        EnchantInfoCommand.register()

        // AutoSprint module: rides the client player tick the bridge already dispatches, so it adds
        // no mixin at all (phase-6 §17).
        AutoSprintHandler.registerEvents()

        // CelestialBlessing (mystery tier) client half: the ring particle's factory + captured sprite
        // provider, the mana-pool tooltip line, and the two receivers (§22/§23).
        CelestialRingParticles.register()
        ManaPoolToolTipHandler.register()
        registerCelestialReceivers()
        InfinitePowerLaserClient.register()

        // Build-identifying line. Every build that changes client behaviour must be distinguishable
        // from the previous one in the log, otherwise "the feature did not work" and "the client is
        // still running the old jar" look identical. (Phase-6 §16 records what that cost.)
        SimpleTweaks.LOGGER.info(
            "{} client ready: damage indicator + /stconfig + /enchantinfo + bow enchantments " +
                "+ celestial ring + enchant-table replace + celestial anti-resistance " +
                "+ starfall world-floor fix + book enchantability + min-enchant top-up " +
                "+ table power cap + book-is-a-book in getPossibleEntries " +
                "+ infinite_power core/aura + dragon part unwrap + laser (bag dropped) " +
                "+ yStartFactor multiplies entity height, not world Y (build=cleanup2)",
            SimpleTweaks.NAME,
        )
    }

    /**
     * Client side of `PacketCelestialRing` and `PacketManaPoolSync`.
     *
     * Both did their work inline in 1.12.2's nested `IMessageHandler#onMessage`. As with
     * [registerDamageIndicatorReceiver], the `context.client().execute { }` hop is redundant on
     * Fabric (receivers already run on the client thread) but is kept so the port stays diffable.
     *
     * <p>1.12.2 cleared the mana cache from Forge's
     * `FMLNetworkEvent.ClientDisconnectionFromServerEvent`; the Fabric equivalent is
     * `ClientPlayConnectionEvents.DISCONNECT`, registered here.
     */
    private fun registerCelestialReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(PacketCelestialRing.ID) { payload, context ->
            context.client().execute {
                CelestialRingParticles.spawnRing(context.client(), payload.ownerUuid)
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(PacketManaPoolSync.ID) { payload, context ->
            context.client().execute {
                ClientManaPoolCache.update(payload.playerUuid, payload.manaPool)
            }
        }

        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            ClientManaPoolCache.clear()
        }
    }

    /**
     * Client side of `PacketDamageIndicator`.
     *
     * 1.12.2 did this inside the packet's nested `IMessageHandler.onMessage`, and had to hop threads
     * with `Minecraft.getMinecraft().addScheduledTask { ... }` because Forge ran the handler on the
     * Netty thread. Fabric already invokes `ClientPlayNetworking` receivers on the client thread, so
     * the hop is redundant — it is kept anyway via `context.client().execute { }` so the port stays
     * diffable against the original and any future move off the client thread stays safe.
     */
    private fun registerDamageIndicatorReceiver() {
        ClientPlayNetworking.registerGlobalReceiver(PacketDamageIndicator.ID) { payload, context ->
            context.client().execute {
                // The config can be off even though the server sent something (it is the same switch
                // on both sides, but a server can have it on while a client has it off).
                if (!DamageIndicatorConfig.enabled) return@execute

                val world = context.client().world ?: return@execute
                val entity = world.getEntityById(payload.entityId) ?: return@execute

                DamageIndicatorManager.addDamage(
                    entity,
                    payload.actualDamage,
                    payload.originalDamage,
                    payload.isHeal,
                    payload.isOverkill,
                    payload.isSelf,
                    payload.maxHealth,
                    payload.realMaxHealth,
                    payload.afterRealHealth,
                )

                if (!damagePacketLogged) {
                    damagePacketLogged = true
                    STLog.log(
                        "DamageIndicator",
                        "packet=received, entity=${entity.type.name.string}, " +
                            "actual=${payload.actualDamage}, original=${payload.originalDamage}, " +
                            "isHeal=${payload.isHeal}, isOverkill=${payload.isOverkill}, " +
                            "isSelf=${payload.isSelf}, maxHealth=${payload.maxHealth}, " +
                            "realMaxHealth=${payload.realMaxHealth}, " +
                            "afterRealHealth=${payload.afterRealHealth}, outcome=received",
                    )
                }
            }
        }
    }
}
