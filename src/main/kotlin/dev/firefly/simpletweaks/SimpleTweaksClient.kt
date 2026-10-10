package dev.firefly.simpletweaks

import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.compat.bridge.ClientEventBridge
import dev.firefly.simpletweaks.client.AutoSprintHandler
import dev.firefly.simpletweaks.client.ClientManaPoolCache
import dev.firefly.simpletweaks.client.ClientScreenOpener
import dev.firefly.simpletweaks.client.EnchantInfoScreen
import dev.firefly.simpletweaks.client.SimpleTweaksConfigScreen
import dev.firefly.simpletweaks.network.packets.PacketOpenModScreen
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

        // `/stconfig` -> in-game config screen, `/enchantinfo` -> enchantment index. Those commands are
        // **server**-side now: a name registered client-side claims the whole root and makes the
        // server's children unreachable (see core/ScreenCommands.kt), so the server sends a request and
        // this side only opens the screen. ClientScreenOpener is what actually puts it up, next tick.
        ClientScreenOpener.register()
        registerScreenReceiver()

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
                "+ yStartFactor multiplies entity height + debug logs off by default " +
                "+ anvil cost cap actually applied " +
                "+ KSP-generated enchantment definitions, first = fast_bow " +
                "+ fast_bow colour + client draw-speed hook " +
                "+ piercing_arrow/crit migrated to KSP + crit-damage priority split " +
                "+ @ModEnchantment enums (category/type/color/slot) " +
                "+ enchant index enumerates the live registry " +
                "+ name colour derived from tier (EnchantmentNameColors deleted) " +
                "+ villagers no longer trade mod enchantments " +
                "+ KSP owns non_treasure/in_enchanting_table/tradeable " +
                "+ @ModEnchantment attributes (prismatic_blessing migrated) " +
                "+ @ModEnchantment.order + order-preserving handler merge + lang no longer generated " +
                "+ @ModEnchantment.jsonEmit (build=cleanup15) " +
                "+ batch 1: 25 empty-effects enchantments migrated " +
                "+ batch 2: 25 damage enchantments migrated " +
                "+ batches 3+4: attributes + jsonEmit carriers; ModEnchantmentKeys deleted (build=cleanup18) " +
                "+ multishot volley applies fast_bow's draw boost + fast_bow relaxes the charge gate " +
                "+ /simple_tweaks enchant (lang'd) + blessing_extension/curse_resistance effect sync " +
                "+ clear_sight 30m night vision " +
                "+ echo_shield soaks reflection without bouncing it " +
                "+ all commands merged under /simple_tweaks + /fst alias " +
                "+ [FST-*] log prefix + inert config switches deleted + event dispatch indexed " +
                "+ multishot volley no longer capped by ammo, costs 1 arrow " +
                "+ heavenly_punishment attack-speed suppression is no longer persisted " +
                "+ all commands server-side (a client-registered root shadowed the server's children) " +
                "(build=cleanup20)",
            SimpleTweaks.NAME,
        )
    }

    /**
     * Client half of [PacketOpenModScreen]: the server names a screen, this side opens it.
     *
     * <p>This is 1.12.2's payload-less `PacketOpenEnchantInfo` generalised to two screens, and it exists
     * for the same reason 1.12.2 had it: the command lives on the server, so opening a client-only
     * screen needs a message. The port's detour through client commands is gone — a shared command root
     * cannot work in Fabric, because a client-registered name is resolved before the server's tree and
     * swallows the server's children (see core/ScreenCommands.kt).
     *
     * <p>Unknown ids are ignored rather than throwing, so a server with a newer build cannot disconnect
     * this client over a screen it does not have.
     */
    private fun registerScreenReceiver() {
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
                    else -> false
                }
                if (opened) STLog.log("Screen") { "screen=${payload.screen}, outcome=opening" }
            }
        }
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
