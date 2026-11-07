package dev.firefly.simpletweaks.core

import dev.firefly.simpletweaks.SimpleTweaks
import dev.firefly.simpletweaks.compat.ForgeEventBus
import dev.firefly.simpletweaks.enchantments.EnchantmentMeta
import dev.firefly.simpletweaks.enchantments.ModEnchantmentKeys
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.enchantments.handlers.common.EnchantAcidAttackHandler
import dev.firefly.simpletweaks.enchantments.handlers.common.EnchantArmorBreakerHandler
import dev.firefly.simpletweaks.enchantments.handlers.common.EnchantAutoSmeltHandler
import dev.firefly.simpletweaks.enchantments.handlers.common.EnchantCombatMasterHandler
import dev.firefly.simpletweaks.enchantments.handlers.common.EnchantMotionBonusHandler
import dev.firefly.simpletweaks.enchantments.handlers.common.EnchantVoidProtectionHandler
import dev.firefly.simpletweaks.enchantments.handlers.epic.EnchantChargedStrikeHandler
import dev.firefly.simpletweaks.enchantments.handlers.epic.EnchantCritDamageHandler
import dev.firefly.simpletweaks.enchantments.handlers.epic.EnchantDamageReductionHandler
import dev.firefly.simpletweaks.enchantments.handlers.epic.EnchantDelayedRecoveryHandler
import dev.firefly.simpletweaks.enchantments.handlers.epic.EnchantGravityStrikeHandler
import dev.firefly.simpletweaks.enchantments.handlers.epic.EnchantTrueDamageHandler
import dev.firefly.simpletweaks.enchantments.handlers.epic.EnchantTunnelingHandler
import dev.firefly.simpletweaks.enchantments.handlers.epic.EnchantVitalityHandler
import dev.firefly.simpletweaks.enchantments.handlers.legendary.EnchantComboHandler
import dev.firefly.simpletweaks.enchantments.handlers.legendary.EnchantDamageLimiterHandler
import dev.firefly.simpletweaks.enchantments.handlers.legendary.EnchantDoubleCritHandler
import dev.firefly.simpletweaks.enchantments.handlers.legendary.EnchantDoubleStrikeHandler
import dev.firefly.simpletweaks.enchantments.handlers.epic.EnchantMultishotHandler
import dev.firefly.simpletweaks.enchantments.handlers.mystery.EnchantCelestialBlessingHandler
import dev.firefly.simpletweaks.enchantments.handlers.rare.EnchantTrackingArrowHandler
import dev.firefly.simpletweaks.enchantments.handlers.legendary.EnchantEchoShotHandler
import dev.firefly.simpletweaks.enchantments.handlers.legendary.EnchantFlightHandler
import dev.firefly.simpletweaks.enchantments.handlers.legendary.EnchantSoulBoundHandler
import dev.firefly.simpletweaks.enchantments.handlers.mythic.EnchantDeathProtectionHandler
import dev.firefly.simpletweaks.enchantments.handlers.mythic.EnchantEchoShieldHandler
import dev.firefly.simpletweaks.enchantments.handlers.mythic.EnchantGrievousWoundsHandler
import dev.firefly.simpletweaks.enchantments.handlers.mythic.EnchantHealingBladeHandler
import dev.firefly.simpletweaks.enchantments.handlers.mythic.EnchantKillAuraHandler
import dev.firefly.simpletweaks.enchantments.handlers.mythic.EnchantStarfallHandler
import dev.firefly.simpletweaks.enchantments.handlers.mythic.infinitepower.FlightHandler
import dev.firefly.simpletweaks.enchantments.handlers.mythic.infinitepower.ForgeHandler
import dev.firefly.simpletweaks.enchantments.handlers.mythic.infinitepower.SoulBindHandler
import dev.firefly.simpletweaks.enchantments.handlers.mythic.infinitepower.ToolHandler
import dev.firefly.simpletweaks.enchantments.handlers.mystery.EnchantHeavenlyPunishmentHandler
import dev.firefly.simpletweaks.enchantments.handlers.rare.EnchantAssassinHandler
import dev.firefly.simpletweaks.enchantments.handlers.rare.EnchantExecuteHandler
import dev.firefly.simpletweaks.enchantments.handlers.rare.EnchantExperienceStealerHandler
import dev.firefly.simpletweaks.enchantments.handlers.rare.EnchantExtraArmorHandler
import dev.firefly.simpletweaks.enchantments.handlers.rare.EnchantFireMasterHandler
import dev.firefly.simpletweaks.enchantments.handlers.rare.EnchantHuntersMarkHandler
import dev.firefly.simpletweaks.enchantments.handlers.rare.EnchantImmortalHandler
import dev.firefly.simpletweaks.enchantments.handlers.rare.EnchantItemFixerHandler
import dev.firefly.simpletweaks.enchantments.handlers.uncommon.EnchantAoeAttackHandler
import dev.firefly.simpletweaks.enchantments.handlers.uncommon.EnchantBloodLustHandler
import dev.firefly.simpletweaks.enchantments.handlers.uncommon.EnchantEffectBonusHandler
import dev.firefly.simpletweaks.enchantments.handlers.uncommon.EnchantHealerHandler
import dev.firefly.simpletweaks.enchantments.handlers.uncommon.EnchantMomentumHandler
import dev.firefly.simpletweaks.enchantments.handlers.uncommon.EnchantRegenerationHandler
import dev.firefly.simpletweaks.enchantments.handlers.uncommon.EnchantResilienceHandler
import dev.firefly.simpletweaks.enchantments.handlers.uncommon.EnchantSaturationHandler
import dev.firefly.simpletweaks.enchantments.handlers.uncommon.EnchantSuperKnockbackHandler
import dev.firefly.simpletweaks.enchantments.handlers.unique.EnchantReForgeHandler
import net.minecraft.enchantment.Enchantment
import net.minecraft.registry.RegistryKey

/**
 * 1.21 port of `core/EnchantmentManager.kt`.
 *
 * The 1.12.2 class had two lists:
 *  - `enchantmentList` (55 `EnchantX` objects) + a `RegistryEvent.Register<Enchantment>` subscriber
 *    → **deleted**. Since 1.21 enchantments are data-driven (JSON files under
 *    `data/simple_tweaks/enchantment/`), there is nothing to register in code and no registry event
 *    to listen for.
 *  - `handlerList` (55 handlers) → kept here, minus the handlers the phase-2.5 analysis removed.
 *
 * Registration is just [initHandlers]; `SimpleTweaks` calls it from `onInitialize`.
 */
object EnchantmentManager {

    /**
     * Handlers to register, in registration order.
     *
     * Batch 1 (common + uncommon) + batch 2 (rare + epic). Grown batch by batch — see
     * `docs/phase3-port-spec.md` for which handlers were dropped, deferred, or are blocked on a seam
     * that does not exist yet.
     *
     * Batch 2 additionally deferred to `deferred/` (seam does not exist yet): `EnchantCritDamageHandler`
     * (needs `CriticalHitEvent`), `EnchantMultishotHandler` (its residual code needs `ArrowLooseEvent`;
     * the volley itself is data-driven via the enchantment JSON), `EnchantPiercingArrowHandler`
     * (needs the `IPiercingArrow` / projectile-hit mixin and `runPlayerAttack`, which is blocked on the
     * crit seam) and `EnchantTrackingArrowHandler` (needs per-projectile custom data, the same
     * `IPiercingArrow` seam, and an accessor for the protected `PersistentProjectileEntity.inGround`).
     *
     * Batch 3 (legendary) adds the five handlers above. Three more legendary files were moved to
     * `deferred/` instead, because their only seam does not exist yet: `EnchantCritHandler` and
     * `EnchantDoubleCritHandler` need `CriticalHitEvent`, and `EnchantSoulBoundHandler` needs
     * `PlayerDropsEvent` (phase-3 spec §5). `EnchantComboHandler` is registered with a known wart:
     * the phase-2.5 plan wanted its "+0.35 x L attack speed" expressed declaratively via
     * `minecraft:attributes`, but `combo.json` does not contain that block yet, so the tick half is
     * still the only source of the bonus.
     *
     * Removed by the phase-2.5 analysis (no code needed — expressed declaratively in the enchantment
     * JSON instead): `EnchantAntiKnockbackHandler`, `EnchantSwiftSneakHandler`.
     *
     * Deferred to the final "hard seams" group: `EnchantMomentumHandler` (needs a block position the
     * BreakSpeed seam cannot supply), `EnchantAutoSmeltHandler` (needs the block-drops seam, which is
     * being replaced by Fabric's `LootTableEvents.MODIFY` rather than a mixin), and
     * `EnchantAoeAttackHandler` (its `runPlayerAttack` helper needs `ForgeHooks.getCriticalHit` /
     * `CriticalHitEvent`, so it is blocked on the crit seam — a cross-file dependency the phase-2.5
     * per-handler classification could not see). Batch 3 did add a 1:1-shaped `runPlayerAttack` to
     * `util/PlayerUtils.kt` for `EnchantEchoShotHandler`, but it is a **documented partial port** (the
     * crit branch is dropped, `triggerEvent` / `ignoreArmorAndPotion` are inert), so re-enabling
     * `EnchantAoeAttackHandler` would inherit those caveats rather than remove them.
     *
     * Batch 4 (mythic) adds the six handlers below. The remaining three mythic files are
     * deliberately absent: `EnchantPrismaticBlessingHandler` and `EnchantUnbreakableHandler` are
     * fully declarative now (their behaviour lives in `prismatic_blessing.json` via
     * `minecraft:attributes` and in `unbreakable.json` via `minecraft:item_damage` set 0 — porting
     * either handler would apply the effect twice), and `EnchantInfinitePowerHandler` is deferred to
     * the final "hard seams" group (it needs `LivingDropsEvent`, `PlayerDropsEvent`,
     * `BlockEvent.HarvestDropsEvent`, `PlayerInteractEvent.LeftClickEmpty` and the bag GUI).
     *
     * > **`EnchantInfinitePowerHandler` is now an explicit DEFERRED item** (the author's call, after a
     * > user report that a Warden could not be killed with it). The root cause, the 1.12.2 semantics
     * > (it kills by setting health directly and never touches the damage pipeline) and the **four
     * > hazards** a future port must handle are written up in `docs/infinite-power-deferred.md`.
     * > Read that file before restarting this work — in particular, blindly reproducing the 1.12.2
     * > `ci.cancel()` leaks crit-seam state and skips the attack-cooldown reset.
     *
     * Batch 5 (the mystery + unique tier that the earlier batch plan forgot to schedule) adds the two
     * handlers below; their seams (`AttackEntityEvent` / `LivingDamageEvent` / `LivingEvent` /
     * `TickEvent` / `PlayerEvent`) were all already implemented. The third file of that tier,
     * `EnchantCelestialBlessingHandler`, is **deferred** — not because of a missing event seam but
     * because it is the only handler in the project that depends on the whole phase-5 network layer
     * (`NetworkManager`, `PacketManaPoolSync`, `PacketCelestialRing`) plus its client half
     * (`ClientManaPoolCache`, `FMLNetworkEvent.ClientDisconnectionFromServerEvent`) and the phase-1c
     * config (`GeneralConfig.enabledSpecialParticles`); none of those exist in this tree yet. It now
     * lives in `deferred/EnchantCelestialBlessingHandler.kt.disabled`.
     */
    private val handlerList = listOf(
        // --- common ---
        EnchantAcidAttackHandler,
        EnchantArmorBreakerHandler,
        EnchantAutoSmeltHandler,
        EnchantCombatMasterHandler,
        EnchantMotionBonusHandler,
        EnchantVoidProtectionHandler,
        // --- uncommon ---
        EnchantAoeAttackHandler,
        EnchantBloodLustHandler,
        EnchantEffectBonusHandler,
        EnchantHealerHandler,
        EnchantMomentumHandler,
        EnchantRegenerationHandler,
        EnchantResilienceHandler,
        EnchantSaturationHandler,
        EnchantSuperKnockbackHandler,
        // --- rare ---
        EnchantAssassinHandler,
        EnchantExecuteHandler,
        EnchantExperienceStealerHandler,
        EnchantExtraArmorHandler,
        EnchantFireMasterHandler,
        EnchantHuntersMarkHandler,
        EnchantImmortalHandler,
        EnchantItemFixerHandler,
        // Bow enchantments of this tier. TrackingArrow is server-only steering; PiercingArrow
        // re-implements the projectile-hit seam (see its KDoc for why a blanket cancel would break).
        //
        // `EnchantPiercingArrowHandler` is no longer listed here: it is declared with `@ModEnchantment`
        // and therefore arrives through `GeneratedEnchantments.HANDLERS`, i.e. appended *after* this
        // list. That is safe for this handler specifically -- its only seam is `EntityJoinWorldEvent`,
        // where it and TrackingArrow touch disjoint state -- but it is exactly why an order-sensitive
        // handler must not be migrated without first expressing its ordering as an `EventPriority`
        // (see the crit note above).
        EnchantTrackingArrowHandler,
        // --- epic ---
        EnchantChargedStrikeHandler,
        EnchantDamageReductionHandler,
        EnchantDelayedRecoveryHandler,
        EnchantGravityStrikeHandler,
        // Bow enchantments: `EnchantMultishotHandler` replaced vanilla's shot via `ArrowLooseEvent`,
        // and is therefore the only user of that seam. Placed with its tier (epic) like the rest.
        EnchantMultishotHandler,
        EnchantTrueDamageHandler,
        EnchantTunnelingHandler,
        EnchantVitalityHandler,
        // --- legendary ---
        EnchantComboHandler,
        // ⚠️ History: this list is NOT purely grouped by tier, because the crit chain *used* to depend
        // on same-priority registration order. ForgeEventBus sorts by EventPriority with a STABLE sort,
        // so listeners sharing a priority run in handlerList order -- and 1.12.2 had
        // EnchantCritHandler at 96, EnchantCritDamageHandler at 97, EnchantDoubleCritHandler at 100.
        // A purely tier-grouped port registered CritDamage (epic) BEFORE Crit (legendary) and silently
        // disabled CritDamage on every forced crit.
        //
        // That coupling is now GONE, on purpose: `EnchantCritHandler` is the only `HIGHEST` listener on
        // `CriticalHitEvent`, while `EnchantCritDamageHandler` runs at `HIGH`
        // (`EnchantDoubleCritHandler` was always `HIGH`). Their relative order is decided by priority,
        // not by position in this list -- which is exactly what let `EnchantCritHandler` move to
        // `@ModEnchantment` even though `GeneratedEnchantments.HANDLERS` is appended *after* this list
        // (see `initHandlers`). It is no longer listed here. `EnchantCritDamageHandler` and
        // `EnchantDoubleCritHandler` stay, in this order, because they share `HIGH` and their relative
        // order is therefore still registration order.
        // Keep the distinct priorities; do not "tidy" them back to the same value.
        EnchantCritDamageHandler,
        EnchantDamageLimiterHandler,
        EnchantDoubleCritHandler,
        EnchantDoubleStrikeHandler,
        EnchantEchoShotHandler,
        EnchantFlightHandler,
        EnchantSoulBoundHandler,
        // --- mythic ---
        EnchantDeathProtectionHandler,
        EnchantEchoShieldHandler,
        EnchantGrievousWoundsHandler,
        EnchantHealingBladeHandler,
        EnchantKillAuraHandler,
        EnchantStarfallHandler,
        // --- infinite_power ---
        // `EnchantInfinitePowerHandler` itself is NOT a Listenable: its one-shot kill is invoked
        // directly from `PlayerEntityAttackMixin` (the same HEAD seam 1.12.2's
        // `MixinEntityPlayerAttack` used, except it does not cancel — see that handler's KDoc), so
        // only the four aura handlers register here.
        //
        // Still missing, each blocked on a seam that does not exist yet: the bag (inventory +
        // container + GUI + its right-click opener) needs a registered `ScreenHandlerType`, an
        // `ExtendedScreenHandlerFactory` and player-persistent storage; the laser needs
        // `PlayerInteractEvent.LeftClickEmpty` plus a packet. `DropHandler` (the 64x drop multiplier)
        // was ported and then **dropped at the author's request**, so nothing else is outstanding
        // there.
        FlightHandler,
        ForgeHandler,
        SoulBindHandler,
        ToolHandler,
        // --- mystery ---
        // CelestialBlessing is the mod's largest handler and the only one that uses the network
        // layer (ring packet + mana sync) plus the config's `enabledSpecialParticles`.
        EnchantCelestialBlessingHandler,
        EnchantHeavenlyPunishmentHandler,
        // --- unique ---
        EnchantReForgeHandler,
    )

    fun initHandlers() {
        // The hand-written list above (the 1.12.2 port, order-sensitive -- see its comments) plus
        // everything declared with @ModEnchantment. KSP renders those into
        // GeneratedEnchantments.HANDLERS, which is what makes "one file per enchantment" true: a new
        // handler is registered without editing this file at all.
        val all = handlerList + GeneratedEnchantments.HANDLERS
        all.forEach { it.registerEvents() }
        SimpleTweaks.LOGGER.info(
            "Registered {} enchantment handler(s) ({} hand-listed + {} @ModEnchantment)",
            all.size, handlerList.size, GeneratedEnchantments.HANDLERS.size,
        )
    }

    /**
     * Every known key, across both generations of this port — see [EnchantmentMeta].
     *
     * This is only used for diagnostics now (enchantments themselves come from the data pack), so a
     * missing entry is not a functional bug — but it *was* one until this became a merge:
     * `ModEnchantmentKeys.ALL` did not contain `fast_bow`, so the enchant index silently omitted it.
     */
    fun getAllKeys(): List<RegistryKey<Enchantment>> = EnchantmentMeta.allKeys()

    @Suppress("unused")
    fun isRegistered(owner: Any): Boolean = ForgeEventBus.isRegistered(owner)
}
