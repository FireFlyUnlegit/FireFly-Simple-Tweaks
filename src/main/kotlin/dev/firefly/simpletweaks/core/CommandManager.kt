package dev.firefly.simpletweaks.core

import dev.firefly.simpletweaks.SimpleTweaks
import dev.firefly.simpletweaks.commands.CommandAttribute
import net.minecraft.command.ICommand
import net.minecraftforge.fml.common.event.FMLServerStartingEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object CommandManager {

    private val commands = mutableListOf<ICommand>()

    private val commandList = listOf(
        CommandAttribute(),
    )

    fun registerCommands() {
        commandList.forEach {
            commands.add(it)
            SimpleTweaks.LOGGER.info("Registering commands: /${it.name}")
        }
        SimpleTweaks.LOGGER.info("Registered ${commands.size} commands")
    }

    @SubscribeEvent
    fun onServerStarting(e: FMLServerStartingEvent) {
        commandList.forEach { e.registerServerCommand(it) }
    }

    fun getAll(): List<ICommand> = commands
}