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
        SimpleMod.LOGGER.info("已注册 ${modules.size} 个模块")
    }

    private fun register(module: Module) {
        modules.add(module)
        module.load()
        if (module.state) {
            module.onEnable()
            SimpleMod.LOGGER.info("模块自动启用: ${module.name}")
        }
        SimpleMod.LOGGER.info("注册模块: ${module.name} (状态: ${if (module.state) "开启" else "关闭"})")
    }

    fun get(name: String): Module? = modules.find { it.name.equals(name, ignoreCase = true) }
    fun getAll(): List<Module> = modules
    fun getByCategory(category: String): List<Module> = modules.filter { it.category == category }
}