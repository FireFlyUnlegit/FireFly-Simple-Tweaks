package dev.firefly.simpletweaks

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.bridge.ClientEventBridge
import dev.firefly.simpletweaks.client.AutoSprintHandler
import dev.firefly.simpletweaks.client.ClientManaPoolCache
import dev.firefly.simpletweaks.client.ClientScreenOpener
import dev.firefly.simpletweaks.client.EnchantInfoScreen
import dev.firefly.simpletweaks.client.SimpleTweaksConfigScreen
import dev.firefly.simpletweaks.network.packets.PacketMobLevel
import dev.firefly.simpletweaks.util.mobs.MobLevelSync
import dev.firefly.simpletweaks.network.packets.PacketOpenModScreen
import dev.firefly.simpletweaks.client.InfinitePowerLaserClient
import dev.firefly.simpletweaks.client.particle.CelestialRingParticles
import dev.firefly.simpletweaks.client.tooltips.EnchantAttributeToolTipHandler
import dev.firefly.simpletweaks.client.tooltips.EnchantDamageToolTipHandler
import dev.firefly.simpletweaks.client.tooltips.ManaPoolToolTipHandler
import dev.firefly.simpletweaks.core.config.DamageIndicatorConfig
import dev.firefly.simpletweaks.core.registerEvents
import dev.firefly.simpletweaks.damageindicator.DamageIndicatorManager
import dev.firefly.simpletweaks.damageindicator.DamageIndicatorRenderer
import dev.firefly.simpletweaks.network.packets.PacketCelestialRing
import dev.firefly.simpletweaks.network.packets.PacketDamageIndicator
import dev.firefly.simpletweaks.network.packets.PacketManaPoolSync
import dev.firefly.simpletweaks.network.packets.PacketResetAttackCooldown
import dev.firefly.simpletweaks.util.attackCharge
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking

/** One-shot guard so the receive-side probe does not flood the log in normal play. */
private var damagePacketLogged = false

/**
 * Count of `PacketMobLevel` receives already logged.
 *
 * `START_TRACKING` fires once per player per mob, so this is only bounded because the probe stops after
 * [MOB_LEVEL_PACKETS_LOGGED]. It exists to answer one question from the log — "did the level ever reach
 * the client?" — which is the difference between a sync bug and a rendering bug (`build=cleanup47`).
 */
private var mobLevelPacketsLogged = 0

private const val MOB_LEVEL_PACKETS_LOGGED = 3

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
        // `minecraft:damage` enchantment effects are applied at hit time and never listed by vanilla,
        // so the mod's own damage bonus needs its own tooltip line (see that file's KDoc for why the
        // attribute-based alternative was left as an option rather than taken).
        EnchantDamageToolTipHandler.register()
        // Enchantment-granted *attributes* (declarative `minecraft:attributes`, plus the bonuses
        // handlers apply themselves) equally never reach vanilla's flat totals: `ItemStack`'s tooltip
        // reads only the item's own `ATTRIBUTE_MODIFIERS` component. Without this, `combo 10` still
        // reads `1.6 攻击速度`.
        EnchantAttributeToolTipHandler.register()
        registerCelestialReceivers()
        registerAttackCooldownReceiver()
        InfinitePowerLaserClient.register()

        // Build-identifying line. Every build that changes client behaviour must be distinguishable
        // from the previous one in the log, otherwise "the feature did not work" and "the client is
        // still running the old jar" look identical. (Phase-6 §16 records what that cost.)
        //
        // `v2.2.0` starts a **new build cycle**: the identifier was reset to `cleanup1` and the
        // feature list was collapsed from the ~2 KB accumulation of 46 batches into a per-version
        // summary. The list is not a changelog -- it only has to answer "which jar is this", and a
        // 2 KB line is no easier to read than a 300-byte one. Reset it again at the next minor version.
        // ⚠️ The enchantment count is **read from the generated table**, never typed as a literal.
        // It was a hard-coded `85` for three batches and went stale the moment three element
        // enchantments were added (`build=cleanup12`) — the log then claimed 85 while `/fst
        // enchantinfo` listed 88, which reads as "the lang/metadata was not updated either". A count
        // that cannot drift is a count nobody has to remember to bump.
        val enchantmentCount = dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments.KEYS.size
        SimpleTweaks.LOGGER.info(
            "{} client ready: {} enchantments + 8 elemental conversions / affinities / reactions " +
                "+ 3 cross-element (ward / resonance / penetration) + elemental mastery + mob levels, " +
                "mod enchantments and mob elements with [Lv.N] tags " +
                "+ per-element damage numbers (optional physical/elemental split) + kill_aura / " +
                "infinite_power / starfall / celestial ring + every command under /simple_tweaks " +
                "(alias /fst) + damage numbers report the health actually removed " +
                "+ every mob gets a level, not only the armed ones " +
                "+ mob enchantments are budgeted by level (1 + level/10 total levels) " +
                "+ debug logs off by default + mod metadata: icon, homepage, ModMenu links " +
                "+ game tests (./gradlew runGametest) for mob levels, stats and damage " +
                "+ enchant index can group by applicable item (Tab) + elemental riders respect shields " +
                "+ mob levels start low and scale with the player's whole loadout " +
                "+ conversion share is 1.5 %/level (cap 7.5 %), element damage bypasses armour again " +
                "+ element damage is no longer exempt from the i-frame gate (only combo cancels it) " +
                "+ reaction names as floating labels with their damage, 18 reactions, " +
                "+ split damage display now emits one number per element, fanned out and staggered " +
                "(v2.2.5, build=cleanup54)",
            SimpleTweaks.NAME,
            enchantmentCount,
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
        // Mob level sync: store it for the name-tag / display-name mixins. No world state to touch, so no
        // `execute` hop is needed -- a map write from the network thread is fine (`MobLevelSync` uses a
        // ConcurrentHashMap for exactly this reason).
        ClientPlayNetworking.registerGlobalReceiver(PacketMobLevel.ID) { payload, _ ->
            MobLevelSync.put(payload.entityId, payload.level)
            if (mobLevelPacketsLogged < MOB_LEVEL_PACKETS_LOGGED) {
                mobLevelPacketsLogged++
                FSTLog.log("MobLevel") {
                    "outcome=level-synced, entityId=${payload.entityId}, level=${payload.level}"
                }
            }
        }
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
                if (opened) FSTLog.log("Screen") { "screen=${payload.screen}, outcome=opening" }
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
            // Entity ids are reused across sessions; a stale level would tag the wrong mob.
            MobLevelSync.clear()
        }
    }

    /**
     * Client half of [PacketResetAttackCooldown] — spend *this* client's attack charge.
     *
     * <p>`runPlayerAttack(..., simulateCooldown = true)` spends the charge on the server, but the HUD
     * attack indicator and vanilla's local hit prediction read the **client's** own `lastAttackedTicks`
     * (`InGameHud` -> `ClientPlayerEntity#getAttackCooldownProgress`), which vanilla only resets from the
     * client's own attack path. Without this the indicator keeps showing a full swing while the server
     * has already spent it — see that packet's KDoc for the bytecode.
     *
     * <p>The probe logs both ends of the transition, so "the packet arrived" and "the charge actually
     * moved" are distinguishable (`chargeAfter` ≈ 0.04 for a 1.6 attack speed, not 0.0: a zero charge
     * is unreachable through this formula, vanilla's `+0.5F` base time is part of it).
     */
    private fun registerAttackCooldownReceiver() {
        ClientPlayNetworking.registerGlobalReceiver(PacketResetAttackCooldown.ID) { _, context ->
            context.client().execute {
                val player = context.client().player ?: return@execute
                val chargeBefore = player.attackCharge
                player.resetLastAttackedTicks()
                FSTLog.log("KillAura") {
                    "outcome=client-charge-spent, chargeBefore=$chargeBefore, " +
                        "chargeAfter=${player.attackCharge}"
                }
            }
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
                    payload.isHeal,
                    payload.isSelf,
                    payload.maxHealth,
                    payload.realMaxHealth,
                    payload.afterRealHealth,
                    payload.elementOrdinal,
                    payload.role,
                    payload.label,
                )

                if (!damagePacketLogged) {
                    damagePacketLogged = true
                    FSTLog.log(
                        "DamageIndicator",
                        "packet=received, entity=${entity.type.name.string}, " +
                            "actual=${payload.actualDamage}, isHeal=${payload.isHeal}, " +
                            "isSelf=${payload.isSelf}, maxHealth=${payload.maxHealth}, " +
                            "realMaxHealth=${payload.realMaxHealth}, " +
                            "afterRealHealth=${payload.afterRealHealth}, " +
                            "element=${payload.elementOrdinal}, role=${payload.role}, outcome=received",
                    )
                }
            }
        }
    }
}
