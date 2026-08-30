package dev.firefly.simplemod.core.event

import dev.firefly.simplemod.core.Listenable
import java.util.concurrent.CopyOnWriteArrayList

object EventManager {
    private val registry = mutableMapOf<Class<out Event>, MutableList<EventHook<*>>>()

    fun <T : Event> register(eventClass: Class<out T>, hook: EventHook<T>) {
        val list = registry.getOrPut(eventClass) { CopyOnWriteArrayList() }
        list.add(hook)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Event> unregister(eventClass: Class<out T>, hook: EventHook<T>) {
        registry[eventClass]?.remove(hook as EventHook<*>)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Event> call(event: T): T {
        val list = registry[event.javaClass] ?: return event
        list.forEach { (it as EventHook<T>).invoke(event) }
        return event
    }

    fun clear() {
        registry.clear()
    }

    data class EventHook<T : Event>(
        val owner: Listenable,
        val action: (T) -> Unit
    ) {
        fun invoke(event: T) {
            if (owner.shouldHandleEvents()) {
                action(event)
            }
        }
    }
}