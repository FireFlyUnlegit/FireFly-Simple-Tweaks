package dev.firefly.simpletweaks

import dev.firefly.simpletweaks.client.AutoSprintHandler
import dev.firefly.simpletweaks.client.ClientScreenOpener
import dev.firefly.simpletweaks.client.InfinitePowerLaserClient
import dev.firefly.simpletweaks.client.MobInfoBarRenderer
import dev.firefly.simpletweaks.client.particle.CelestialRingParticles
import dev.firefly.simpletweaks.client.tooltips.ArcaneEchoTooltipHandler
import dev.firefly.simpletweaks.client.tooltips.DevourTooltipHandler
import dev.firefly.simpletweaks.client.tooltips.EnchantAttributeToolTipHandler
import dev.firefly.simpletweaks.client.tooltips.EnchantAttributeTooltipFolder
import dev.firefly.simpletweaks.client.tooltips.EnchantDamageToolTipHandler
import dev.firefly.simpletweaks.client.tooltips.ManaPoolToolTipHandler
import dev.firefly.simpletweaks.compat.bridge.ClientEventBridge
import dev.firefly.simpletweaks.core.registerEvents
import dev.firefly.simpletweaks.damageindicator.DamageIndicatorRenderer
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.network.packets.PacketCelestialReceiver
import dev.firefly.simpletweaks.network.packets.PacketDamageIndicatorReceiver
import dev.firefly.simpletweaks.network.packets.PacketHandshakeReceiver
import dev.firefly.simpletweaks.network.packets.PacketMobLevelReceiver
import dev.firefly.simpletweaks.network.packets.PacketOpenModScreenReceiver
import dev.firefly.simpletweaks.network.packets.PacketResetAttackCooldownReceiver
import dev.firefly.simpletweaks.util.elements.ElementReactions
import dev.firefly.simpletweaks.util.elements.ElementType
import net.fabricmc.api.ClientModInitializer

/**
 * Client-only entrypoint. Fabric splits initialisation into common ([SimpleTweaks]) and client
 * ([SimpleTweaksClient]) instead of Forge's `@SideOnly` + `FMLPreInitializationEvent` single hook.
 *
 * Everything reached from here may safely touch client-only classes, because fabric-loader never loads
 * this class on a dedicated server.
 *
 * This class used to also *own* the seven network receivers; they now live in `network/packets/` next
 * to the packets they decode (`Packet*Receiver`), so a packet and its client half can be read and
 * changed together. What stays here is wiring only.
 */
class SimpleTweaksClient : ClientModInitializer {

    override fun onInitializeClient() {
        SimpleTweaks.LOGGER.info("{} initialising client tick bridge...", SimpleTweaks.NAME)
        ClientEventBridge.register()

        // Network halves, one per packet family.
        PacketHandshakeReceiver.register()
        PacketDamageIndicatorReceiver.register()
        PacketMobLevelReceiver.register()
        PacketOpenModScreenReceiver.register()
        PacketCelestialReceiver.register()
        PacketResetAttackCooldownReceiver.register()

        DamageIndicatorRenderer.register()
        MobInfoBarRenderer.register()

        // `/stconfig` -> in-game config screen, `/enchantinfo` -> enchantment index. Those commands are
        // **server**-side now: a name registered client-side claims the whole root and makes the
        // server's children unreachable (see core/ScreenCommands.kt), so the server sends a request and
        // this side only opens the screen. ClientScreenOpener is what actually puts it up, next tick.
        ClientScreenOpener.register()

        // AutoSprint module: rides the client player tick the bridge already dispatches, so it adds no
        // mixin at all (phase-6 §17).
        AutoSprintHandler.registerEvents()

        // CelestialBlessing (mystery tier) client half: the ring particle's factory + captured sprite
        // provider, and the mana-pool tooltip line.
        CelestialRingParticles.register()
        ManaPoolToolTipHandler.register()
        // `minecraft:damage` enchantment effects are applied at hit time and never listed by vanilla,
        // so the mod's own damage bonus needs its own tooltip line.
        EnchantDamageToolTipHandler.register()
        // Enchantment-granted *attributes* equally never reach vanilla's flat totals: `ItemStack`'s
        // tooltip reads only the item's own `ATTRIBUTE_MODIFIERS` component.
        EnchantAttributeToolTipHandler.register()
        // Whatever mirrors enchantment attributes into the stack's tooltip prints one line per effect;
        // sum them per attribute so a fully enchanted armour piece stays readable (Shift expands).
        EnchantAttributeTooltipFolder.register()
        // devour keeps its stacks in the item's CUSTOM_DATA, which nothing else displays.
        DevourTooltipHandler.register()
        // arcane_echo's bonuses are runtime modifiers, so the live block has to be printed by us.
        ArcaneEchoTooltipHandler.register()
        InfinitePowerLaserClient.register()

        logReady()
    }

    /**
     * Build-identifying line. Every build that changes client behaviour must be distinguishable from
     * the previous one in the log, otherwise "the feature did not work" and "the client is still running
     * the old jar" look identical.
     *
     * The identifier is `devVer.<major>.<minor>.<patch>`, replacing the old `cleanupN` counter. The patch
     * counts batches inside one release version; **whenever a version bump is requested the whole
     * identifier resets to `devVer.1.0.0`** and the feature list collapses back to a per-version summary.
     *
     * Every count in the line is derived from the table it describes. They used to be literals and went
     * stale: the line claimed 85 enchantments after three more were added, and claimed 35 advanced forms
     * while the generated table held 37. A count that cannot drift is a count nobody has to remember.
     */
    private fun logReady() {
        val enchantments = GeneratedEnchantments.KEYS.size
        val advancedForms = GeneratedEnchantments.KEYS.count { it.value.path.startsWith("advanced_") }
        val elements = ElementType.entries.size
        val reactions = ElementReactions.ALL.map { it.key }.distinct().size
        SimpleTweaks.LOGGER.info(
            "{} client ready: {} enchantments ({} advanced forms) + {} elements ({} reactions) " +
                "+ levelled mobs with a floating name / health / armor bar " +
                "+ HUD bars, damage numbers and enchantment tooltips " +
                "+ every command under /simple_tweaks (alias /fst) " +
                "(v2.5.0, build=devVer.1.0.0)",
            SimpleTweaks.NAME,
            enchantments,
            advancedForms,
            elements,
            reactions,
        )
    }
}
