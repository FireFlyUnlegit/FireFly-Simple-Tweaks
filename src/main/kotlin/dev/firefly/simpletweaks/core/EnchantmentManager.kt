package dev.firefly.simpletweaks.core

import dev.firefly.simpletweaks.SimpleTweaks
import dev.firefly.simpletweaks.compat.ForgeEventBus
import dev.firefly.simpletweaks.enchantments.EnchantmentMeta
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.enchantments.handlers.mythic.infinitepower.FlightHandler
import dev.firefly.simpletweaks.enchantments.handlers.mythic.infinitepower.ForgeHandler
import dev.firefly.simpletweaks.enchantments.handlers.mythic.infinitepower.SoulBindHandler
import dev.firefly.simpletweaks.enchantments.handlers.mythic.infinitepower.ToolHandler
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
     * Handlers to register, as `order to handler`.
     *
     * The numbers are **the original 1.12.2 registration order** and they are load bearing, not
     * decorative: `ForgeEventBus` sorts by `EventPriority` with a stable sort, so within one priority the
     * dispatch order *is* registration order, and a handler that changes place can silently stop seeing
     * state a sibling sets. They are written out explicitly rather than implied by list position so that
     * deleting a migrated entry cannot shift every entry after it.
     *
     * When a handler moves to `@ModEnchantment` its number goes with it (`order = N`) and the entry here
     * is deleted; `initHandlers` merges both sources by that number.
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
    private val handlerList: List<Pair<Int, Listenable>> = listOf(
        // --- common ---
        // --- uncommon ---
        // --- rare ---
        // Bow enchantments of this tier. TrackingArrow is server-only steering; PiercingArrow
        // re-implements the projectile-hit seam (see its KDoc for why a blanket cancel would break).
        //
        // `EnchantPiercingArrowHandler` is no longer listed here: it is declared with `@ModEnchantment`
        // and therefore arrives through `GeneratedEnchantments.HANDLERS`. That is safe for this handler
        // specifically -- its only seam is `EntityJoinWorldEvent`, where it and TrackingArrow touch
        // disjoint state -- and it is exactly why a migrated handler must carry the number it had here.
        // --- epic ---
        // Bow enchantments: `EnchantMultishotHandler` replaced vanilla's shot via `ArrowLooseEvent`,
        // and is therefore the only user of that seam. Placed with its tier (epic) like the rest.
        // --- legendary ---
        // ⚠️ History: the numbers below are the 1.12.2 order (Crit 96 / CritDamage 97 / DoubleCrit 100),
        // because same-priority listeners dispatch in registration order and the crit chain depended on
        // CritDamage running after Crit. That coupling is now expressed by `EventPriority` instead --
        // Crit is the only `HIGHEST` listener on `CriticalHitEvent`, CritDamage runs at `HIGH` -- which is
        // what let `EnchantCritHandler` become a `@ModEnchantment` declaration carrying its number.
        // Keep the distinct priorities; do not "tidy" them back to the same value.
        // --- mythic ---
        // --- infinite_power ---
        // `EnchantInfinitePowerHandler` itself is NOT a Listenable: its one-shot kill is invoked
        // directly from `PlayerEntityAttackMixin` (the same HEAD seam 1.12.2's
        // `MixinEntityPlayerAttack` used, except it does not cancel — see that handler's KDoc), so
        // only the four aura handlers register here. They are the reason this list must keep explicit
        // numbers: they sit in the middle of the sequence, so a migrated handler that merely *appended*
        // itself would come after them.
        //
        // Still missing, each blocked on a seam that does not exist yet: the bag (inventory +
        // container + GUI + its right-click opener) needs a registered `ScreenHandlerType`, an
        // `ExtendedScreenHandlerFactory` and player-persistent storage; the laser needs
        // `PlayerInteractEvent.LeftClickEmpty` plus a packet. `DropHandler` (the 64x drop multiplier)
        // was ported and then **dropped at the author's request**, so nothing else is outstanding
        // there.
        46 to FlightHandler,
        47 to ForgeHandler,
        48 to SoulBindHandler,
        49 to ToolHandler,
        // --- mystery ---
        // CelestialBlessing is the mod's largest handler and the only one that uses the network
        // layer (ring packet + mana sync) plus the config's `enabledSpecialParticles`.
        // --- unique ---
    )

    fun initHandlers() {
        // Hand-listed survivors and `@ModEnchantment` declarations, merged **by order** rather than by
        // concatenation. The distinction is not cosmetic: `infinite_power`'s four aura handlers stay
        // listed above (they are not the object carrying that enchantment's annotation) and they sit in
        // the middle of the original sequence, so appending the declared handlers would move every
        // listener after them. `order` is `Int.MAX_VALUE` for a brand-new declaration, which is how a new
        // KSP enchantment lands at the end without anyone renumbering anything.
        // ORDERS.zip(HANDLERS), not the other way round: the result must be `Pair<Int, Listenable>` to
        // match `handlerList` above, or the two lists have no common element type.
        val declared = GeneratedEnchantments.HANDLER_ORDERS.zip(GeneratedEnchantments.HANDLERS)
        val all = (handlerList + declared).sortedBy { it.first }.map { it.second }
        all.forEach { it.registerEvents() }
        SimpleTweaks.LOGGER.info(
            "Registered {} enchantment handler(s) ({} hand-listed + {} @ModEnchantment)",
            all.size, handlerList.size, GeneratedEnchantments.HANDLERS.size,
        )
    }

    /**
     * Every known key — see [EnchantmentMeta].
     *
     * This is only used for diagnostics now (enchantments themselves come from the data pack), so a
     * missing entry is not a functional bug — but it *was* one: the old generated key table omitted
     * `fast_bow`, so the enchant index silently left it out.
     */
    fun getAllKeys(): List<RegistryKey<Enchantment>> = EnchantmentMeta.allKeys()

    @Suppress("unused")
    fun isRegistered(owner: Any): Boolean = ForgeEventBus.isRegistered(owner)
}
