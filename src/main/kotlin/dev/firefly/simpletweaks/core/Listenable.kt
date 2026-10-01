package dev.firefly.simpletweaks.core

import dev.firefly.simpletweaks.core.event.Event
import dev.firefly.simpletweaks.core.event.EventManager
import net.minecraftforge.common.MinecraftForge

interface Listenable {
    fun shouldHandleEvents(): Boolean = true
    fun init() {}
}

internal inline fun <reified T : Event> Listenable.handler(
    noinline action: (T) -> Unit
): EventManager.EventHook<T> {
    val hook = EventManager.EventHook(this, action)
    EventManager.register(T::class.java, hook)
    if (this is Module) {
        this._handlers.add(T::class.java to hook)
    }
    return hook
}

@Suppress("UNCHECKED_CAST")
fun Listenable.unregisterAll() {
    if (this is Module) {
        this._handlers.forEach { (eventClass, hook) ->
            EventManager.unregister(eventClass, hook as EventManager.EventHook<Event>)
        }
        this._handlers.clear()
    }
}
fun Listenable.registerToForge() {
    MinecraftForge.EVENT_BUS.register(this)
    this.init()
}
