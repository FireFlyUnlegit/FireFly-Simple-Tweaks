package dev.firefly.simplemod.core

import dev.firefly.simplemod.SimpleMod
import dev.firefly.simplemod.commands.CommandAttribute
import net.minecraft.command.ICommand
import net.minecraftforge.common.MinecraftForge
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
            SimpleMod.LOGGER.info("Registering commands: /${it.name}")
        }
        SimpleMod.LOGGER.info("Registered ${commands.size} commands")
    }

    @SubscribeEvent
    fun onServerStarting(e: FMLServerStartingEvent) {
        commandList.forEach { e.registerServerCommand(it) }
    }

    fun getAll(): List<ICommand> = commands
}