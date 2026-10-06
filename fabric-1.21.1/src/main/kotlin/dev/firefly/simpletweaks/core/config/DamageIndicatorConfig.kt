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
