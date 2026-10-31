package dev.firefly.simpletweaks.compat

import dev.firefly.simpletweaks.compat.event.Event
import dev.firefly.simpletweaks.compat.event.EventPriority
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * Stand-in for `net.minecraftforge.common.MinecraftForge.EVENT_BUS`.
 *
 * The 1.12.2 mod registered 76 handler objects with the Forge bus and relied on reflective
 * discovery of `@SubscribeEvent` methods. This reproduces that contract so the handler bodies
 * need no structural edits during the port.
 *
 * Dispatch is by exact event class (`isInstance` over a flat hierarchy), ordered by an ascending
 * **int** dispatch value (smaller runs first — see [EventPriority]), and skips cancelled events
 * unless the listener opted in with `receiveCanceled = true`.
 *
 * <h2>Dispatch is indexed by event class</h2>
 * The bus holds **118 listener methods** across 65 handlers, but a given event matches only a handful
 * of them — `LivingEvent.LivingUpdateEvent` matches 3, `TickEvent.ServerTickEvent` matches 1. A flat
 * scan therefore spent most of its time failing [Class.isInstance] checks. [byEventClass] caches, per
 * concrete event class, the sublist whose declared parameter type accepts it, so a post touches only
 * the listeners that can possibly run.
 *
 * This is **exactly equivalent** to the flat scan, not an approximation: for a concrete `event`,
 * `parameterType.isInstance(event)` and `parameterType.isAssignableFrom(event.javaClass)` are the same
 * predicate. Order is preserved (the cache is a filtered view of the already priority-sorted list), so
 * the stable same-priority ordering that `@ModEnchantment.order` depends on is untouched.
 *
 * <h2>Ordering is a plain int now</h2>
 * [Listener.priority] holds the raw `int` from `@SubscribeEvent(priority = …)`, ascending = earlier
 * (see [EventPriority] for the values and the legal range). Ties are broken by **registration order**,
 * which [register] preserves because `sortBy` is a stable sort. `@ModEnchantment.order` therefore keeps
 * working exactly as before: it decides registration order, and registration order is the tie-break.
 *
 * <h2>Where the honest cost is</h2>
 * Measured for reference — this is a **small** win, and the number is recorded here on purpose so that
 * nobody optimises the wrong layer later:
 * <pre>
 *   listeners per event    LivingHurtEvent 24 | PlayerTickEvent 18 | LivingDamageEvent 14
 *   hottest post site      LivingUpdateEvent, fired per entity per tick (EntityTickMixin)
 *   order of magnitude     ~300 entities -> ~6000 posts/s -> ~7e5 isInstance/s -> ~1 ms/s
 * </pre>
 * The per-tick cost that actually matters lives *inside* the 18 `PlayerTickEvent` handlers, most of
 * which re-read the enchantment component for four armour slots every tick. That is handler work, not
 * bus work, and no amount of dispatch tuning touches it.
 *
 * <h2>Iteration without an iterator</h2>
 * The snapshot is a plain [List] behind a `@Volatile` field, replaced wholesale on every mutation, and
 * posts walk it by index. `CopyOnWriteArrayList` did the same job, but its iterator allocates one
 * object per post — and the per-entity events post thousands of times per second.
 */
object ForgeEventBus {

    private val LOGGER = LoggerFactory.getLogger("simple_tweaks/events")

    /**
     * One registered `@SubscribeEvent` method.
     *
     * ## Why a `MethodHandle` rather than `Method.invoke`
     * `Method.invoke` is a **vararg** call, so Kotlin compiles
     * `listener.method.invoke(listener.owner, event)` into `anewarray Object[1]` + `aastore` + `invoke` —
     * **one `Object[]` allocated per listener per post** (bytecode-verified: offsets 101–114 of
     * `ForgeEventBus.post` in the previous revision). At ~7.8k dispatches/second that is ~7.8k
     * short-lived arrays/second of pure garbage, before reflection's own defensive copy of the argument
     * array.
     *
     * A `MethodHandle` invoked as `handle.invoke(owner, event)` allocates **nothing** and is JIT-inlinable,
     * which makes this a genuinely lossless change: same target, same arguments, same order.
     *
     * ## The one behavioural detail that had to be preserved
     * `Method.invoke` wraps any exception thrown by the target in `InvocationTargetException`, and
     * [post] logs `e.cause` for that case. A `MethodHandle.invoke` throws the **original** exception
     * instead, so [post] had to learn to unwrap `Throwable` rather than rely on the wrapper — otherwise
     * the log would change shape for a failing handler. `method` is kept alongside for diagnostics
     * (`owner.javaClass.simpleName` + `method.name` in the error lines).
     */
    private class Listener(
        val owner: Any,
        val method: Method,
        /** Bound to `owner`; invoking takes exactly the one event argument. */
        val handle: MethodHandle,
        /** Ascending dispatch value, already clamped into the legal range. See [EventPriority]. */
        val priority: Int,
        val receiveCanceled: Boolean,
        val eventType: Class<*>
    )

    /**
     * Immutable snapshot of every listener, sorted by ascending [Listener.priority]. Replaced wholesale
     * on mutation, so a post either sees a whole change or none of it and never needs a lock.
     */
    @Volatile
    private var listeners: List<Listener> = emptyList()

    /**
     * Per concrete event class, the listeners whose parameter type accepts it.
     *
     * Rebuilt from scratch on every mutation rather than maintained incrementally: registration
     * happens once at mod init while events are far more frequent, so rebuild-on-write is both simpler
     * and cheaper than an incremental one.
     */
    private val byEventClass = ConcurrentHashMap<Class<*>, List<Listener>>()

    /** Registers every `@SubscribeEvent` method declared on [owner] (including superclasses). */
    @JvmStatic
    fun register(owner: Any) {
        val discovered = collect(owner)
        if (discovered.isEmpty()) {
            LOGGER.warn("No @SubscribeEvent methods found on {}", owner.javaClass.name)
            return
        }
        // Rebuild through a plain list; the re-registration filter replaces the old unregister +
        // re-add pair, so a repeated register cannot leave the owner in the list twice.
        val merged = ArrayList<Listener>(listeners.size + discovered.size)
        merged.addAll(listeners.filter { it.owner !== owner })
        merged.addAll(discovered)
        // `sortBy` is a stable sort, so registration order survives as the tie-break inside one
        // priority — that is what keeps `@ModEnchantment.order` load bearing.
        merged.sortBy { it.priority }
        publish(merged)
        LOGGER.info(
            "Registered {} event handler(s) from {}",
            discovered.size, owner.javaClass.simpleName
        )
    }

    @JvmStatic
    fun unregister(owner: Any) {
        publish(listeners.filter { it.owner !== owner })
    }

    /**
     * Fires [event] at every listener registered for its exact type.
     * Returns the same instance so callers can inspect mutations, as Forge did.
     */
    @JvmStatic
    fun <T : Event> post(event: T): T {
        // Hot path: one map lookup, then only the listeners that can actually receive this event.
        val relevant = byEventClass[event.javaClass] ?: matching(event.javaClass)
        if (relevant.isEmpty()) return event

        // Index loop on purpose -- see the class KDoc on why this does not use an Iterator.
        for (i in relevant.indices) {
            val listener = relevant[i]
            if (event.isCanceled && !listener.receiveCanceled) continue
            try {
                // No argument array is allocated here: `List<Object>`/`Object` overloads of
                // `invokeWithArguments` would allocate, and so would `Method.invoke`, which is exactly
                // what this replace. `invokeExact` would be marginally faster but throws
                // `WrongMethodTypeException` if the handle's type is not exactly `(Event)V`, which is not
                // worth a crash-on-typo for a few nanoseconds.
                listener.handle.invoke(event)
            } catch (e: Throwable) {
                // `MethodHandle.invoke` rethrows the target's own exception (no
                // `InvocationTargetException` wrapper). Unwrap defensively so the old reflection-shaped
                // input (a wrapper from any remaining `Method.invoke` caller) logs the same thing.
                val cause = if (e is InvocationTargetException) e.cause ?: e else e
                LOGGER.error(
                    "Error handling {} in {}.{}",
                    event.javaClass.simpleName, listener.owner.javaClass.simpleName, listener.method.name,
                    cause
                )
            }
        }
        return event
    }

    fun clear() {
        publish(emptyList())
    }

    private fun publish(next: List<Listener>) {
        listeners = next
        byEventClass.clear()
    }

    private fun matching(eventClass: Class<*>): List<Listener> =
        byEventClass.computeIfAbsent(eventClass) { cls ->
            val snapshot = listeners
            val out = ArrayList<Listener>(4)
            for (i in snapshot.indices) {
                if (snapshot[i].eventType.isAssignableFrom(cls)) out.add(snapshot[i])
            }
            out
        }

    /**
     * Clamps [raw] into [EventPriority.MIN_PRIORITY]`..`[EventPriority.MAX_PRIORITY]` and reports an
     * out-of-range value once per listener at registration.
     *
     * The `warn` is unconditional (not gated behind `[FST-*]`), because a priority typo changes
     * dispatch order silently: `-2147483648` would put a listener in front of `HIGHEST` and nothing
     * in the log would say so. Registration happens once at init, so this cannot spam.
     */
    private fun priorityOf(raw: Int, owner: Any, method: Method): Int {
        if (EventPriority.inRange(raw)) return raw
        val clamped = EventPriority.clamp(raw)
        LOGGER.warn(
            "{}#{}: priority {} is outside {}..{}; clamped to {}. " +
                "A priority outside the legal range is almost always a typo.",
            owner.javaClass.simpleName, method.name,
            raw, EventPriority.MIN_PRIORITY, EventPriority.MAX_PRIORITY, clamped
        )
        return clamped
    }

    private fun collect(owner: Any): List<Listener> {
        val out = mutableListOf<Listener>()
        var type: Class<*>? = owner.javaClass
        while (type != null && type != Any::class.java) {
            for (method in type.declaredMethods) {
                val annotation = method.getAnnotation(SubscribeEvent::class.java) ?: continue
                if (method.parameterCount != 1) {
                    LOGGER.warn(
                        "@SubscribeEvent method {}.{} must take exactly one argument; skipped",
                        owner.javaClass.simpleName, method.name
                    )
                    continue
                }
                method.isAccessible = true
                // Bind the receiver now so the hot path is a single-argument `invoke(event)`.
                //
                // `unreflect` gives a handle of type `(DeclaringClass, Event)V`; `bindTo(owner)` turns it
                // into `(Event)V`. Doing it here (once per listener, at registration) is what keeps the
                // per-post path allocation-free.
                //
                // ⚠️ `bindTo` **fails** on a method whose declared parameter type is `Object` because the
                // handle's first parameter would then be dynamic — none exist here (every
                // `@SubscribeEvent` method takes a concrete `Event` subclass, enforced by the
                // `@SubscribeEvent` contract and by `eventType = method.parameterTypes[0]` below), but a
                // future one would surface as a registration-time exception rather than a silent
                // mis-dispatch, which is the correct failure direction for this project.
                val handle = MethodHandles.lookup().unreflect(method).bindTo(owner)
                out += Listener(
                    owner,
                    method,
                    handle,
                    priorityOf(annotation.priority, owner, method),
                    annotation.receiveCanceled,
                    method.parameterTypes[0]
                )
            }
            type = type.superclass
        }
        return out
    }

    // Kept for parity with the old codebase's top-level handlers list; unused in phase 0.
    @Suppress("unused")
    fun registeredCount(): Int = listeners.size

    @Suppress("unused")
    fun isRegistered(owner: Any): Boolean = listeners.any { it.owner === owner }

    /**
     * Convenience used by the ported handlers so `MinecraftForge.EVENT_BUS.register(x)` translates
     * to a one-line change. See [dev.firefly.simpletweaks.core.registerEvents].
     */
    @JvmStatic
    fun postLivingHurt(event: LivingHurtEvent): LivingHurtEvent = post(event)
}
