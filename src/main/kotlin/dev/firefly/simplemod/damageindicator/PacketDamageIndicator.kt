package dev.firefly.simplemod.damageindicator

import io.netty.buffer.ByteBuf
import net.minecraft.entity.Entity
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext

class PacketDamageIndicator : IMessage {
    var entityId: Int = 0
    var actualDamage: Float = 0f
    var originalDamage: Float = 0f
    var isHeal: Boolean = false
    var isOverkill: Boolean = false
    var isSelf: Boolean = false
    var maxHealth: Float = 0f
    var realMaxHealth: Float = 0f
    var afterRealHealth: Float = 0f

    constructor()
    constructor(
        entity: Entity,
        actualDamage: Float,
        originalDamage: Float,
        isHeal: Boolean = false,
        isOverkill: Boolean = false,
        isSelf: Boolean = false,
        maxHealth: Float = 0f,
        realMaxHealth: Float = 0f,
        afterRealHealth: Float = 0f
    ) {
        this.entityId = entity.entityId
        this.actualDamage = actualDamage
        this.originalDamage = originalDamage
        this.isHeal = isHeal
        this.isOverkill = isOverkill
        this.isSelf = isSelf
        this.maxHealth = maxHealth
        this.realMaxHealth = realMaxHealth
        this.afterRealHealth = afterRealHealth
    }

    override fun fromBytes(buf: ByteBuf) {
        entityId = buf.readInt()
        actualDamage = buf.readFloat()
        originalDamage = buf.readFloat()
        isHeal = buf.readBoolean()
        isOverkill = buf.readBoolean()
        isSelf = buf.readBoolean()
        maxHealth = buf.readFloat()
        realMaxHealth = buf.readFloat()
        afterRealHealth = buf.readFloat()
    }

    override fun toBytes(buf: ByteBuf) {
        buf.writeInt(entityId)
        buf.writeFloat(actualDamage)
        buf.writeFloat(originalDamage)
        buf.writeBoolean(isHeal)
        buf.writeBoolean(isOverkill)
        buf.writeBoolean(isSelf)
        buf.writeFloat(maxHealth)
        buf.writeFloat(realMaxHealth)
        buf.writeFloat(afterRealHealth)
    }

    class Handler : IMessageHandler<PacketDamageIndicator, IMessage> {
        override fun onMessage(message: PacketDamageIndicator, ctx: MessageContext): IMessage? {
            ctx.clientHandler?.let {
                net.minecraft.client.Minecraft.getMinecraft().addScheduledTask {
                    val world = net.minecraft.client.Minecraft.getMinecraft().world
                    val entity = world.getEntityByID(message.entityId)
                    if (entity != null) {
                        DamageIndicatorManager.addDamage(
                            entity,
                            message.actualDamage,
                            message.originalDamage,
                            message.isHeal,
                            message.isOverkill,
                            message.isSelf,
                            message.maxHealth,
                            message.realMaxHealth,
                            message.afterRealHealth
                        )
                    }
                }
            }
            return null
        }
    }
}