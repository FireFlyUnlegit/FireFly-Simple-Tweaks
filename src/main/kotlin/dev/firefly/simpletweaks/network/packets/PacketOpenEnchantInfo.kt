package dev.firefly.simpletweaks.network.packets

import dev.firefly.simpletweaks.gui.GuiEnchantInfo
import io.netty.buffer.ByteBuf
import net.minecraft.client.Minecraft
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

class PacketOpenEnchantInfo : IMessage {
    override fun fromBytes(buf: ByteBuf) {}
    override fun toBytes(buf: ByteBuf) {}

    class Handler : IMessageHandler<PacketOpenEnchantInfo, IMessage> {
        @SideOnly(Side.CLIENT)
        override fun onMessage(message: PacketOpenEnchantInfo, ctx: MessageContext): IMessage? {
            Minecraft.getMinecraft().addScheduledTask {
                Minecraft.getMinecraft().displayGuiScreen(GuiEnchantInfo())
            }
            return null
        }
    }
}