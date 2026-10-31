package dev.firefly.simpletweaks.compat.event

/**
 * Minimal re-creation of the Forge event base type.
 *
 * Why this exists: the 1.12.2 codebase has 144 `@SubscribeEvent` methods spread over 76 files and
 * depends on 27 distinct Forge event classes. Fabric API has no equivalent for the most important
 * of these (notably `LivingHurtEvent` / `LivingDamageEvent`, whose *mutable* damage amount and
 * cancellation are used by 43 call sites). Rather than redesign 55 handlers individually, the
 * migration keeps the Forge event *shape* and re-creates the seams with a small number of mixins.
 * That concentrates all 1.21 API translation into this one layer.
 *
 * Event classes in this module are deliberately a FLAT hierarchy (each event extends [Event]
 * directly, never another event) so that dispatch is by exact class, matching Forge's behaviour.
 */
abstract class Event {
    /** Forge semantics: only cancelable events may be cancelled. */
    open val isCancelable: Boolean get() = false

    /**
     * Exposed to Java as `isCanceled()` / `setCanceled(boolean)`, matching the Forge member names
     * the existing handlers already call.
     */
    var isCanceled: Boolean = false
}

/** Marker base for events whose outcome can be suppressed by a handler. */
abstract class CancelableEvent : Event() {
    override val isCancelable: Boolean get() = true
}

/**
 * Listener running order, as **plain ints instead of Forge's five-value enum**.
 *
 * ## Semantics
 * The bus dispatches in **ascending** value: a smaller number runs earlier, a larger number later.
 * `@SubscribeEvent(priority = …)` takes an `Int`; omit it and the listener runs at [NORMAL] (`0`),
 * which is also where a brand-new handler belongs.
 *
 * | constant | value | Forge name it replaces | meaning |
 * |---|---|---|---|
 * | [HIGHEST] | `-100` | `EventPriority.HIGHEST` | runs before everything else (sees untouched state) |
 * | [HIGH] | `-50` | `EventPriority.HIGH` | early, but after [HIGHEST] |
 * | [NORMAL] | `0` | `EventPriority.NORMAL` | default |
 * | [LOW] | `50` | `EventPriority.LOW` | late; reads values other listeners have already written |
 * | [LOWEST] | `100` | `EventPriority.LOWEST` | last |
 *
 * ## Why ints
 * The enum only had five slots, so expressing "between HIGH and NORMAL" was impossible, and the only
 * lever in between was the *second* sort key (`@ModEnchantment.order`), which is really about
 * registration grouping. With ints any number can go anywhere: `priority = -25` sits between [HIGH]
 * and [NORMAL].
 *
 * ## The range, and why register-time clamping exists
 * The legal range is [MIN_PRIORITY]`..`[MAX_PRIORITY], i.e. `-2147483637 .. 2147483647` (it leaves
 * `Int.MIN_VALUE + 10` headroom at the bottom so internal arithmetic on the value cannot overflow,
 * and takes the whole positive side up to `Int.MAX_VALUE`). A value outside it is **clamped to the
 * nearest end** by [ForgeEventBus] when the listener is registered, and that clamping is logged as a
 * `warn` — an out-of-range number is a typo far more often than a deliberate choice, and silently
 * letting `-2147483648` through would move a listener to the very front of the bus with no trace.
 *
 * ## Compatibility
 * These are `Int` constants in an `object`, so Kotlin and Java call sites both read them as plain
 * numbers (`priority = EventPriority.HIGHEST` still compiles at the use site, but the value that
 * ends up in the annotation is an `Int`, which is what `@SubscribeEvent.priority` now expects).
 * **A brand-new handler should just omit `priority` and get `0`.**
 */
object EventPriority {

    /** Runs first. Replaces Forge's `EventPriority.HIGHEST`. */
    const val HIGHEST: Int = -100

    /** Runs early, after [HIGHEST]. Replaces Forge's `EventPriority.HIGH`. */
    const val HIGH: Int = -50

    /** The default for a listener that does not specify anything. Replaces Forge's `NORMAL`. */
    const val NORMAL: Int = 0

    /** Runs late. Replaces Forge's `EventPriority.LOW`. */
    const val LOW: Int = 50

    /** Runs last. Replaces Forge's `EventPriority.LOWEST`. */
    const val LOWEST: Int = 100

    /** Lowest accepted value — see the class KDoc on why it is not `Int.MIN_VALUE`. */
    const val MIN_PRIORITY: Int = -2147483637

    /** Highest accepted value. */
    const val MAX_PRIORITY: Int = Int.MAX_VALUE

    /**
     * [value] clamped into [MIN_PRIORITY]`..`[MAX_PRIORITY].
     *
     * Written as a plain comparison rather than `MathHelper.clamp` so this file stays dependent on
     * nothing; it is called once per listener method at registration, never on the hot path.
     */
    @JvmStatic
    fun clamp(value: Int): Int = when {
        value < MIN_PRIORITY -> MIN_PRIORITY
        value > MAX_PRIORITY -> MAX_PRIORITY
        else -> value
    }

    /** `true` when [value] is already inside the legal range (i.e. [clamp] would not change it). */
    @JvmStatic
    fun inRange(value: Int): Boolean = value in MIN_PRIORITY..MAX_PRIORITY
}
