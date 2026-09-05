package dev.firefly.simplemod.network

import dev.firefly.simplemod.damageindicator.PacketDamageIndicator
import dev.firefly.simplemod.enchantments.enchantment_handlers.infinitepower.PacketLaser
import net.minecraftforge.fml.common.network.NetworkRegistry
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper
import net.minecraftforge.fml.relauncher.Side

object NetworkManager {
    const val CHANNEL = "simplemod"
    val wrapper: SimpleNetworkWrapper = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL)
    private var discriminator = 0

    fun registerPackets() {
        // 服务端 ← 客户端
        wrapper.registerMessage(
            PacketLaser.Handler::class.java,
            PacketLaser::class.java,
            discriminator++,
            Side.SERVER
        )

        // 客户端 ← 服务端
        wrapper.registerMessage(
            PacketDamageIndicator.Handler::class.java,
            PacketDamageIndicator::class.java,
            discriminator++,
            Side.CLIENT
        )
    }

    // 发送到服务端
    fun sendToServer(packet: Any) {
        wrapper.sendToServer(packet as IMessage?)
    }

    // 发送到客户端
    fun sendToClient(packet: Any, player: net.minecraft.entity.player.EntityPlayerMP) {
        wrapper.sendTo(packet as IMessage?, player)
    }

    // 发送到所有玩家
    fun sendToAll(packet: Any) {
        wrapper.sendToAll(packet as IMessage?)
    }
}