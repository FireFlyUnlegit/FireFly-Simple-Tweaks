package dev.firefly.simpletweaks.core.config

/**
 * 1.21 port of `core/config/DamageIndicatorConfig.java` (9 options).
 *
 * Same Forge → Fabric mapping as [GeneralConfig]; see that file for the full rationale. This one is
 * **client-only in effect** (it configures the floating damage numbers), but it lives in the shared
 * config file because 1.12.2 registered it with the same `&#64;Config(modid = "simple_tweaks")`.
 *
 * The `&#64;RangeInt` / `&#64;RangeDouble` bounds from the 1.12.2 annotations are: duration 10..500,
 * riseDuration 5..50, maxDistance 16..256, scale 0.5..10.0, yStartFactor 0.5..2.0. They are enforced
 * on load by [SimpleTweaksConfig].
 */
object DamageIndicatorConfig {

    /** `Enable Damage Indicator` — show damage numbers when attacking mobs. */
    @JvmStatic
    var enabled: Boolean = true

    /** `Indicator with symbol(+/-)`. */
    @JvmStatic
    var symbol: Boolean = true

    /** `Display Duration (Ticks)` — 20 ticks = 1 second. Range 10..500. */
    @JvmStatic
    var duration: Int = 40

    /** `Rise Duration (Ticks)` — how long the number rises before stopping. Range 5..50. */
    @JvmStatic
    var riseDuration: Int = 10

    /** `Max Distance` — render distance in blocks. Range 16..256. */
    @JvmStatic
    var maxDistance: Int = 64

    /** `Scale` — base scale of the numbers. Range 0.5..10.0. */
    @JvmStatic
    var scale: Double = 1.2

    /** `Percentage Mode` — show damage/health as a percentage instead of absolute values. */
    @JvmStatic
    var percentageMode: Boolean = false

    /**
     * `Min Health Change` — the smallest health delta that still spawns a number (`build=cleanup26`).
     *
     * **0.0 (default) = no filtering**, i.e. exactly the behaviour before this option existed. Any
     * positive value suppresses every damage/heal whose **absolute** change is strictly below it, so
     * `1.0` hides everything smaller than half a heart (poison ticks, saturation healing, small
     * chip damage) while true hits still show.
     *
     * <p><b>Who reads it:</b> [dev.firefly.simpletweaks.damageindicator.DamageIndicatorHandler], on
     * the **server**, where the delta is computed by comparing health between ticks. The packet only
     * carries the final amount, so the client cannot apply this filter. On a dedicated server the
     * server's `config/simple_tweaks.json` therefore governs; in single player both sides read the
     * same file.
     *
     * <p>Range 0..1000 (enforced on load), step 1 in `stconfig` — the unit is the same as displayed
     * numbers (1.0 = half a heart).
     */
    @JvmStatic
    var minHealthChange: Double = 0.0

    /**
     * Split a hit into its **non-elemental** and **elemental** parts as two separate floating numbers.
     *
     * Default **false** — the merged single number is the pre-`cleanup32` behaviour and stays the
     * default so nothing changes for anyone who does not ask for this.
     *
     * <p><b>Where the split comes from.</b> The indicator is a per-tick health differ: it only knows
     * *how much* health was lost, not of what. The breakdown is collected on
     * `LivingDamageEvent`, which fires once per `LivingEntity#applyDamage` — i.e. exactly once per
     * damage instance, so a converted hit arrives as one non-elemental instance and one elemental
     * instance and their amounts are summed separately per entity per tick.
     *
     * <p><b>What this changes:</b>
     * - with the option on, the two components are emitted as their own numbers (non-elemental = the
     *   indicator's red, elemental = that element's colour, drawn slightly higher and without the second
     *   health line so the pair stays readable);
     * - a hit with only one component still produces one number;
     * - **heals** are never split — they produce no damage event, so they keep going through the health
     *   differ;
     * - `minHealthChange` is applied **per component** rather than to the total, so a tiny elemental
     *   tick can be hidden while the plain part still shows.
     *
     * <p><b>Who reads it:</b> [dev.firefly.simpletweaks.damageindicator.DamageIndicatorHandler], on the
     * **server** (that is where the damage events are). Both numbers travel in the same packet shape, so
     * turning this on needs no client rebuild — but it does need the server's config.
     */
    @JvmStatic
    var splitElemental: Boolean = false

    /** `Show Text Shadow`. */
    @JvmStatic
    var showShadow: Boolean = true

    /**
     * `yStartFactor` — vertical spawn offset, expressed in **entity heights** above the entity's feet.
     *
     * Range 0.5..2.0, default 1.0 = exactly the top of the hitbox, i.e. the 1.12.2 behaviour.
     *
     * The 1.12.2 original was `startY * factor` where `startY = entity.posY + entity.height`, i.e. the
     * factor multiplied the *absolute world Y*. That is harmless at y≈64 but pathological elsewhere:
     * at y=70 with `factor = 2.0` the number spawns near y=143, which is further from the camera than
     * `maxDistance` and is therefore **never drawn at all** — the feature silently disappears, and
     * because `cancelVanillaDamageIndicator` is on by default there is no vanilla fallback either.
     * The 1.21.1 port multiplies the height term only, so no value in range can push the number out of
     * render range.
     */
    @JvmStatic
    var yStartFactor: Double = 1.0
}
