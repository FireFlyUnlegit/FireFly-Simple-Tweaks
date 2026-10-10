package dev.firefly.simpletweaks.core.config

/**
 * 1.21 port of `core/config/GeneralConfig.java`.
 *
 * Every field here has a real consumer; switches that had none were removed (see the note before the
 * AutoSprint block). "Config key exists" must never be mistaken for "something reads it" — that
 * mistake is recorded three times in this project's history.
 *
 * <h2>Forge → Fabric mapping</h2>
 * 1.12.2 used Forge's annotation config system, which produced `config/simple_tweaks.cfg` with a
 * `GeneralSettings` category and exposed the options as **public static fields**:
 * <pre>
 *   &#64;Config(modid = "simple_tweaks", name = "GeneralSettings", category = "GeneralSettings")
 *   public class GeneralConfig { public static boolean disableAnvilCostLimit = true; ... }
 * </pre>
 * 1.21 has no built-in config API and this port deliberately does **not** add Cloth Config (it would
 * be a new dependency and a new GUI, i.e. phase-6 work). Instead the options live in
 * [SimpleTweaksConfig]'s JSON file and are mirrored here as `&#64;JvmStatic` properties, so:
 * <ul>
 *   <li>Kotlin reads them exactly like 1.12.2 did: {@code GeneralConfig.disableAnvilCostLimit}</li>
 *   <li>Java mixins read {@code GeneralConfig.getDisableAnvilCostLimit()} instead of
 *       {@code GeneralConfig.disableAnvilCostLimit} — the only call-site edit this port costs.</li>
 * </ul>
 *
 * <h2>Why the ranges are enforced in code</h2>
 * Forge's `&#64;RangeInt` / `&#64;RangeDouble` silently clamped hand-edited config files. Since the JSON
 * file is user-editable, the same clamping is applied on load — see [SimpleTweaksConfig]. The bounds
 * and defaults below are copied from the 1.12.2 annotations, not invented.
 *
 * <h2>The `&#64;Config.Name` strings are kept as comments</h2>
 * They were the keys in the old `.cfg`; they are recorded next to each field because they are the
 * only human-readable description the original ever had. The JSON keys are the **field names**,
 * which is what the code reads and what a diff against the 1.12.2 source can be checked against.
 */
object GeneralConfig {

    /**
     * `Cancel Vanilla Damage Indicator` — hide vanilla's own damage-indicator particle, because this
     * mod's damage indicator (`damageindicator/DamageIndicatorRenderer`) replaces it.
     *
     * <h2>Scope, and why it is narrower than 1.12.2</h2>
     * 1.12.2 implemented this with a single **unfiltered** `@Redirect` on `WorldServer#spawnParticle`
     * inside `EntityPlayer#attackTargetEntityWithCurrentItem`, so it also suppressed the crit and
     * enchanted-hit particles. The port deliberately does **not**: the author scoped it to the
     * `DAMAGE_INDICATOR` particle only, and an earlier revision that cancelled
     * `addCritParticles` / `addEnchantedHitParticles` was deleted for exactly that reason.
     * The seam is `mixin/PlayerEntityDamageIndicatorParticleMixin`.
     *
     * <p>Note this is unrelated to [dev.firefly.simpletweaks.core.config.DamageIndicatorConfig.enabled],
     * which turns this mod's *own* floating numbers on and off. The two switches control different
     * things and are not duplicates: `enabled` = "does this mod draw numbers", this = "does vanilla
     * draw its particle".
     */
    @JvmStatic
    var cancelVanillaDamageIndicator: Boolean = true

    /** `Disable Anvil Cost Limit` — remove the "Too Expensive" cap. */    @JvmStatic
    var disableAnvilCostLimit: Boolean = true

    /** `Max Anvil Cost` (`&#64;RangeInt(min = 1)`) — only applies while the limit is disabled. */
    @JvmStatic
    var maxAnvilCost: Int = Int.MAX_VALUE

    /** `Disable Enchantment Table Enchantability Limit` — remove the vanilla power cap of 15. */
    @JvmStatic
    var disableEnchantmentTableLimit: Boolean = true

    /** `Max Enchantment Table Power` (`&#64;RangeInt(min = 1)`) — vanilla is 15. */
    @JvmStatic
    var maxEnchantmentPower: Int = 15

    /** `Enabled Enchantments' Color` — let enchantment names render in their own colour. */
    @JvmStatic
    var enabledEnchantmentColor: Boolean = true

    /** `Enabled Enchantments' Special Particles`. */
    @JvmStatic
    var enabledSpecialParticles: Boolean = true

    // --------------------------------------------------------- removed: inert switches
    //
    // Two feature switches used to live here with no implementation behind them. Both were deleted
    // on the author's decision, keeping only the record:
    //
    //   * `anvilDisenchant` — the 1.12.2 anvil-based disenchanter. Never ported; 1.21's grindstone
    //     covers the use case, so the switch went along with its config-GUI row and its lang key.
    //     (The 1.12.2 source had given it the `Enabled Enchantments' Special Particles` display name
    //     by copy-paste — an existing `.cfg` would have carried that duplicate.)
    //   * `noFovEnabled` / `noFovValue` — the NoFOV module. A working 1.21.1 seam existed
    //     (`GameRenderer#getFov(Camera,F,Z)D` RETURN, verified through all three gates) and was then
    //     deleted: NoFOV was never a core feature, and forcing the value also flattens the
    //     **spyglass zoom**, which is reached through the same `fovMultiplier` — so the module
    //     genuinely conflicts with 1.21's FOV model rather than merely needing a port.
    //
    // Deleting the keys costs a hand-edited config nothing: [SimpleTweaksConfig] ignores unknown keys
    // and writes the normalised file back on load, so stale entries simply disappear.

    // ------------------------------------------------------------- AutoSprint module
    //
    // 1.12.2 had `modules/AutoSprint.kt`: `object AutoSprint : Module("Sprint", "Movement")` with a
    // single `boolean("OmniSprint", false)`. Flattened into two config fields for the same reason as
    // NoFov — the 229-line `Module` framework is not worth porting for one small module, and the
    // config GUI now edits plain fields directly.

    /**
     * `Sprint` module enabled — keeps the player sprinting while moving forward.
     *
     * Defaults to **false**, matching 1.12.2's `Module.state`, which initialises to `false`.
     */
    @JvmStatic
    var autoSprintEnabled: Boolean = false

    /**
     * `Sprint` → `boolean("OmniSprint", false)`.
     *
     * When true, sprinting is forced regardless of movement input or sneaking; 1.12.2 wrote the
     * condition as `moveForward > 0.1f && !isSneaking || omniSprint`, and Kotlin's precedence makes
     * that `(moveForward > 0.1f && !isSneaking) || omniSprint`.
     */
    @JvmStatic
    var autoSprintOmniSprint: Boolean = false
}
