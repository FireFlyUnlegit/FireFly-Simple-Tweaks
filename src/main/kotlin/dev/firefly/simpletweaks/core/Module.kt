package dev.firefly.simpletweaks.core

import dev.firefly.simpletweaks.SimpleTweaks
import dev.firefly.simpletweaks.core.configs.Configurable
import dev.firefly.simpletweaks.core.event.Event
import dev.firefly.simpletweaks.core.event.EventManager
import org.lwjgl.input.Keyboard

abstract class Module(
    val moduleName: String,
    val category: String,
    val description: String = "",
    val defaultKey: Int = Keyboard.KEY_NONE
) : Configurable(moduleName), Listenable {

    internal val _handlers = mutableListOf<Pair<Class<out Event>, EventManager.EventHook<*>>>()

    var keyBind: Int = defaultKey
        set(value) {
            field = value
            save()
        }

    var state: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (value) onEnable() else onDisable()
            save()
            SimpleTweaks.LOGGER.info("Module '$moduleName' ${if (value) "启用" else "禁用"}")
        }

    open fun onEnable() {}
    open fun onDisable() {}

    override fun shouldHandleEvents(): Boolean = state

    fun toggle() { state = !state }
    fun isEnabled(): Boolean = state

    fun getKeyName(): String {
        return if (keyBind == Keyboard.KEY_NONE) "NONE" else Keyboard.getKeyName(keyBind) ?: "NONE"
    }
}