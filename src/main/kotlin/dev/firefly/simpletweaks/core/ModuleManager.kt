package dev.firefly.simpletweaks.core

import dev.firefly.simpletweaks.SimpleTweaks
import dev.firefly.simpletweaks.modules.AutoSprint
import dev.firefly.simpletweaks.modules.NoFov
import dev.firefly.simpletweaks.modules.Velocity

object ModuleManager {
    private val modules = mutableListOf<Module>()

    private val moduleList = listOf(
        AutoSprint,
        NoFov,
        Velocity
    )
    fun registerModules() {
        moduleList.forEach {
            register(it)
        }
        SimpleTweaks.LOGGER.info("Registered ${modules.size} Modules")
    }

    private fun register(module: Module) {
        modules.add(module)
        module.load()
        if (module.state) {
            module.onEnable()
            SimpleTweaks.LOGGER.info("Module Enabled Automatically: ${module.name}")
        }
        SimpleTweaks.LOGGER.info("RegisterModule: ${module.name} (State: ${if (module.state) "Enabled" else "Disabled"})")
    }

    fun get(name: String): Module? = modules.find { it.name.equals(name, ignoreCase = true) }
    fun getAll(): List<Module> = modules
    fun getByCategory(category: String): List<Module> = modules.filter { it.category == category }
}