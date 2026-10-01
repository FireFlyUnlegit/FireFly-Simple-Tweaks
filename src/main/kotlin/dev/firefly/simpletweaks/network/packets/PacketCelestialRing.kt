package dev.firefly.simpletweaks.network.packets

import dev.firefly.simpletweaks.client.particle.CelestialRingParticle
import io.netty.buffer.ByteBuf
import net.minecraft.client.Minecraft
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import java.util.*

class PacketCelestialRing : IMessage {
    lateinit var ownerUUID: UUID
    constructor()
    constructor(uuid: UUID) { ownerUUID = uuid }

    override fun fromBytes(buf: ByteBuf) {
        ownerUUID = UUID(buf.readLong(), buf.readLong())
    }
    override fun toBytes(buf: ByteBuf) {
        buf.writeLong(ownerUUID.mostSignificantBits)
        buf.writeLong(ownerUUID.leastSignificantBits)
    }

    class Handler : IMessageHandler<PacketCelestialRing, IMessage?> {
        @SideOnly(Side.CLIENT)
        override fun onMessage(message: PacketCelestialRing, ctx: MessageContext): IMessage? {
            val mc = Minecraft.getMinecraft()
            mc.addScheduledTask {
                val world = mc.world ?: return@addScheduledTask
                val player = world.getPlayerEntityByUUID(message.ownerUUID) ?: return@addScheduledTask
                for (i in 0 until 8) {
                    val angleOffset = i * (2 * Math.PI / 8)
                    mc.effectRenderer.addEffect(
                        CelestialRingParticle(
                            world, message.ownerUUID,
                            angleOffset,
                            1.0,
                            1.0,
                            0.05
                        )
                    )
                }
            }
            return null
        }
    }
}