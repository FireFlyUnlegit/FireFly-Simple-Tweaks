package dev.firefly.simplemod.enchantments.handlers.mythic.infinitepower

import dev.firefly.simplemod.enchantments.handlers.mythic.EnchantInfinitePowerHandler
import io.netty.buffer.ByteBuf
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.math.Vec3d
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext

class PacketLaser : IMessage {
    lateinit var direction: Vec3d
    var playerId: Int = 0

    constructor()
    constructor(player: EntityPlayer, dir: Vec3d) {
        this.playerId = player.entityId
        this.direction = dir
    }

    override fun fromBytes(buf: ByteBuf) {
        playerId = buf.readInt()
        val x = buf.readDouble()
        val y = buf.readDouble()
        val z = buf.readDouble()
        direction = Vec3d(x, y, z)
    }

    override fun toBytes(buf: ByteBuf) {
        buf.writeInt(playerId)
        buf.writeDouble(direction.x)
        buf.writeDouble(direction.y)
        buf.writeDouble(direction.z)
    }

    class Handler : IMessageHandler<PacketLaser, IMessage> {
        override fun onMessage(message: PacketLaser, ctx: MessageContext): IMessage? {
            ctx.serverHandler?.let {
                ctx.serverHandler.player.server.addScheduledTask {
                    val player = ctx.serverHandler.player.world.getEntityByID(message.playerId)
                    if (player is EntityPlayer) {
                        EnchantInfinitePowerHandler.fireLaser(player, message.direction, player.world)
                    }
                }
            }
            return null
        }
    }
}