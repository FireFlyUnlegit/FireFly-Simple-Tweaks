package dev.firefly.simplemod.core

import dev.firefly.simplemod.SimpleMod
import dev.firefly.simplemod.core.configs.Configurable
import dev.firefly.simplemod.core.event.Event
import dev.firefly.simplemod.core.event.EventManager
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
            SimpleMod.LOGGER.info("Module '$moduleName' ${if (value) "启用" else "禁用"}")
        }

    open fun onEnable() {}
    open fun onDisable() {}

    override fun shouldHandleEvents(): Boolean = state

    fun toggle() { state = !state }
    fun isEnabled(): Boolean = state

    // ✅ 添加 getKeyName 函数
    fun getKeyName(): String {
        return if (keyBind == Keyboard.KEY_NONE) "NONE" else Keyboard.getKeyName(keyBind) ?: "NONE"
    }
}