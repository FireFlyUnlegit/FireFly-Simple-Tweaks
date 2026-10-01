package dev.firefly.simpletweaks.network.packets

import dev.firefly.simpletweaks.client.ClientManaPoolCache
import io.netty.buffer.ByteBuf
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import java.util.*

class PacketManaPoolSync : IMessage {
    var playerUUID: UUID = UUID(0, 0)
    var manaPool: Float = 0f

    constructor()
    constructor(uuid: UUID, pool: Float) {
        this.playerUUID = uuid
        this.manaPool = pool
    }

    override fun fromBytes(buf: ByteBuf) {
        playerUUID = UUID(buf.readLong(), buf.readLong())
        manaPool = buf.readFloat()
    }

    override fun toBytes(buf: ByteBuf) {
        buf.writeLong(playerUUID.mostSignificantBits)
        buf.writeLong(playerUUID.leastSignificantBits)
        buf.writeFloat(manaPool)
    }

    class Handler : IMessageHandler<PacketManaPoolSync, IMessage?> {
        @SideOnly(Side.CLIENT)
        override fun onMessage(message: PacketManaPoolSync, ctx: MessageContext): IMessage? {
            ClientManaPoolCache.update(message.playerUUID, message.manaPool)
            return null
        }
    }
}