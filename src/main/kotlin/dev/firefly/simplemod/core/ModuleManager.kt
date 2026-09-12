package dev.firefly.simplemod.core

import dev.firefly.simplemod.SimpleMod
import dev.firefly.simplemod.modules.AutoSprint
import dev.firefly.simplemod.modules.NoFov
import dev.firefly.simplemod.modules.Velocity

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
        SimpleMod.LOGGER.info("Registered ${modules.size} Modules")
    }

    private fun register(module: Module) {
        modules.add(module)
        module.load()
        if (module.state) {
            module.onEnable()
            SimpleMod.LOGGER.info("Module Enabled Automatically: ${module.name}")
        }
        SimpleMod.LOGGER.info("RegisterModule: ${module.name} (State: ${if (module.state) "Enabled" else "Disabled"})")
    }

    fun get(name: String): Module? = modules.find { it.name.equals(name, ignoreCase = true) }
    fun getAll(): List<Module> = modules
    fun getByCategory(category: String): List<Module> = modules.filter { it.category == category }
}