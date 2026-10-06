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
 * Declaration order is Forge's priority order: HIGHEST runs first.
 * [ForgeEventBus] dispatches by ascending `ordinal`.
 */
enum class EventPriority {
    HIGHEST,
    HIGH,
    NORMAL,
    LOW,
    LOWEST
}
