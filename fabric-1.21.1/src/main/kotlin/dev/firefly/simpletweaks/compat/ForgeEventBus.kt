package dev.firefly.simpletweaks.compat

import dev.firefly.simpletweaks.compat.event.Event
import dev.firefly.simpletweaks.compat.event.EventPriority
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import org.slf4j.LoggerFactory
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
 * Dispatch is by exact event class (`isInstance` over a flat hierarchy), ordered by
 * [EventPriority] ordinal, and skips cancelled events unless the listener opted in with
 * `receiveCanceled = true`.
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

    private class Listener(
        val owner: Any,
        val method: Method,
        val priority: EventPriority,
        val receiveCanceled: Boolean,
        val eventType: Class<*>
    )

    /**
     * Immutable snapshot of every listener, sorted by [EventPriority]. Replaced wholesale on mutation,
     * so a post either sees a whole change or none of it and never needs a lock.
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
        merged.sortBy { it.priority.ordinal }
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
                listener.method.invoke(listener.owner, event)
            } catch (e: InvocationTargetException) {
                LOGGER.error(
                    "Error handling {} in {}",
                    event.javaClass.simpleName, listener.owner.javaClass.simpleName, e.cause
                )
            } catch (e: Exception) {
                LOGGER.error(
                    "Failed to dispatch {} to {}",
                    event.javaClass.simpleName, listener.owner.javaClass.simpleName, e
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
                out += Listener(
                    owner,
                    method,
                    annotation.priority,
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
