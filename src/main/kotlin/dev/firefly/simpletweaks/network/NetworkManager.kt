package dev.firefly.simpletweaks.network

import dev.firefly.simpletweaks.damageindicator.PacketDamageIndicator
import dev.firefly.simpletweaks.enchantments.handlers.mythic.infinitepower.PacketLaser
import dev.firefly.simpletweaks.network.packets.PacketCelestialRing
import dev.firefly.simpletweaks.network.packets.PacketManaPoolSync
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraftforge.fml.common.network.NetworkRegistry
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper
import net.minecraftforge.fml.relauncher.Side

object NetworkManager {
    const val CHANNEL = "simple_tweaks"
    val wrapper: SimpleNetworkWrapper = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL)
    private var discriminator = 0

    fun registerPackets() {
        wrapper.registerMessage(
            PacketLaser.Handler::class.java,
            PacketLaser::class.java,
            discriminator++,
            Side.SERVER
        )

        wrapper.registerMessage(
            PacketDamageIndicator.Handler::class.java,
            PacketDamageIndicator::class.java,
            discriminator++,
            Side.CLIENT
        )

        wrapper.registerMessage(
            PacketCelestialRing.Handler::class.java,
            PacketCelestialRing::class.java,
            discriminator++,
            Side.CLIENT
        )
        wrapper.registerMessage(
            PacketManaPoolSync.Handler::class.java,
            PacketManaPoolSync::class.java,
            discriminator++,
            Side.CLIENT
        )
    }

    fun sendToServer(packet: Any) {
        wrapper.sendToServer(packet as IMessage)
    }

    fun sendToClient(packet: Any, player: EntityPlayerMP) {
        wrapper.sendTo(packet as IMessage, player)
    }

    fun sendToAll(packet: Any) {
        wrapper.sendToAll(packet as IMessage)
    }

    fun sendToAllAround(
        packet: Any,
        dimension: Int,
        x: Double, y: Double, z: Double,
        range: Double
    ) {
        val target = NetworkRegistry.TargetPoint(dimension, x, y, z, range)
        wrapper.sendToAllAround(packet as IMessage, target)
    }
}