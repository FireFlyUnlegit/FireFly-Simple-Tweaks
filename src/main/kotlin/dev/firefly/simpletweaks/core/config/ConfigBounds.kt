package dev.firefly.simpletweaks.core.config

/**
 * The **only** place a config option's allowed range is defined.
 *
 * ## Why this file exists
 * Before `build=cleanup44` the range was written twice: once as `coerceIn(...)` in
 * [SimpleTweaksConfig.readDamageIndicator] and once as `min`/`max` at the call site in the config
 * screen. Two copies of a bound is the same failure this repository records for enchantment weights and
 * reaction tables — widening one side leaves the other silently narrowing it back, and the symptom
 * ("I typed 600 for duration and it came back 500") looks like a bug in the text field.
 *
 * The screen and the loader now both read these values, so a range change is a one-line edit here.
 *
 * ## Why the upper bounds are much larger than before
 * The old numbers came from porting the 1.12.2 `@RangeInt`/`@RangeDouble` annotations, which were chosen
 * for a GUI that only had +/- buttons: anything unreachable by clicking was pointless. With text input
 * there is no reason to stop at 500 ticks or a scale of 10, so the ceilings are raised to values that are
 * still safe at runtime (see each entry) rather than to what a button could reach.
 */
object ConfigBounds {

    /** Damage indicator lifetime, ticks. 1 keeps "almost instant" available, 2000 is 100 s. */
    val DAMAGE_DURATION = 1..2000

    /** Number pop-in time, ticks. Must stay below [DAMAGE_DURATION] in practice; not enforced. */
    val DAMAGE_RISE = 1..200

    /** Cut-off distance in blocks. 1024 covers a full render distance without going silly. */
    val DAMAGE_DISTANCE = 1..1024

    /** Text scale multiplier. 0.05 is "barely legible", 50 is "covers the screen" (clamped per-number too). */
    val DAMAGE_SCALE = 0.05..50.0

    /** Below this health delta no number is spawned. 1e6 = effectively off. */
    val DAMAGE_MIN_HEALTH_CHANGE = 0.0..1_000_000.0

    /** Vertical offset factor relative to entity height. */
    val DAMAGE_Y_START = 0.0..10.0

    /** Enchanting-table power needed for the top tier. 10 000 needs absurd bookshelf walls, by design. */
    val ENCHANT_POWER = 1..10_000

    /** Anvil cost ceiling; `Int.MAX_VALUE` means "no limit". */
    val ANVIL_COST = 1..Int.MAX_VALUE

    /** Level-up chance in percent, for the three mob level-up triggers. */
    val LEVEL_UP_PERCENT = 0..100

    /** Persistent `/fst difficulty modify` value (see `MobEnhancementConfig.difficultyModifier`). */
    val DIFFICULTY_MODIFIER = -1000.0..10_000.0

    /** Decimals shown by the bar HUD numbers; 0 is "integers only", 4 is the finest readable step. */
    val BAR_DECIMALS = 0..4
}
