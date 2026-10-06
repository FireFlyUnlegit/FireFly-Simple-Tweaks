package dev.firefly.simpletweaks.commands

import dev.firefly.simpletweaks.network.NetworkManager
import dev.firefly.simpletweaks.network.packets.PacketOpenEnchantInfo
import net.minecraft.command.CommandBase
import net.minecraft.command.CommandException
import net.minecraft.command.ICommandSender
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.server.MinecraftServer

object CommandEnchantInfo : CommandBase() {

    override fun getName() = "enchantinfo"

    override fun getUsage(sender: ICommandSender) = "/enchantinfo"

    override fun getRequiredPermissionLevel() = 0

    override fun getAliases(): List<String> = listOf("ei", "enchinfo")

    override fun execute(server: MinecraftServer, sender: ICommandSender, args: Array<String>) {
        val player = sender as? EntityPlayerMP
            ?: throw CommandException("Player only.")
        NetworkManager.sendToClient(PacketOpenEnchantInfo(), player)
    }
}