package dev.firefly.simpletweaks.compat

import dev.firefly.simpletweaks.compat.event.Event
import dev.firefly.simpletweaks.compat.event.EventPriority
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import org.slf4j.LoggerFactory
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.CopyOnWriteArrayList

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

    private val listeners = CopyOnWriteArrayList<Listener>()

    /** Registers every `@SubscribeEvent` method declared on [owner] (including superclasses). */
    @JvmStatic
    fun register(owner: Any) {
        val discovered = collect(owner)
        if (discovered.isEmpty()) {
            LOGGER.warn("No @SubscribeEvent methods found on {}", owner.javaClass.name)
            return
        }
        unregister(owner)
        // Rebuild through a plain list: CopyOnWriteArrayList's own sort() is not something to rely
        // on, and registration happens once at init so the cost is irrelevant.
        val merged = ArrayList<Listener>(listeners.size + discovered.size)
        merged.addAll(listeners)
        merged.addAll(discovered)
        merged.sortBy { it.priority.ordinal }
        listeners.clear()
        listeners.addAll(merged)
        LOGGER.info(
            "Registered {} event handler(s) from {}",
            discovered.size, owner.javaClass.simpleName
        )
    }

    @JvmStatic
    fun unregister(owner: Any) {
        listeners.removeIf { it.owner === owner }
    }

    /**
     * Fires [event] at every listener registered for its exact type.
     * Returns the same instance so callers can inspect mutations, as Forge did.
     */
    @JvmStatic
    fun <T : Event> post(event: T): T {
        for (listener in listeners) {
            if (!listener.eventType.isInstance(event)) continue
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

    fun clear() = listeners.clear()

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
