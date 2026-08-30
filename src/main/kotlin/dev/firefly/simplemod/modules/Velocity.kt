package dev.firefly.simplemod.modules

import dev.firefly.simplemod.core.Module
import dev.firefly.simplemod.core.event.PacketReceiveEvent
import dev.firefly.simplemod.core.event.PlayerUpdateEvent
import dev.firefly.simplemod.core.handler
import dev.firefly.simplemod.util.getItemSpecificEnchantLevel
import dev.firefly.simplemod.util.realMotionX
import dev.firefly.simplemod.util.realMotionY
import dev.firefly.simplemod.util.realMotionZ
import net.minecraft.client.Minecraft
import net.minecraft.init.Enchantments
import net.minecraft.network.play.server.SPacketEntityVelocity

object Velocity : Module("Velocity","Combat") {
    val mode = choices("Mode",arrayOf("JumpReset","Modify","Attack"),"JumpReset")
    val h = float("Horizontal",0.6f,0.0f,1.0f)
    val v = float("Vertical",1.0f,0.0f,1.0f)
    val attackCount = int("AttackCount",1,1,10)
    val smart = boolean("SmartAttack",true)
    private var hasReceivedVelocity = false
    private var attackCounter = 0
    init {
        val mc = Minecraft.getMinecraft()
        handler<PlayerUpdateEvent> {
            val p = mc.player?: return@handler
            when (mode.get()) {
                "JumpReset"->if (p.hurtTime == 9 && p.onGround && p.isSprinting && hasReceivedVelocity) {
                    p.jump()
                } else if (hasReceivedVelocity) hasReceivedVelocity = false
                "Attack" -> if(p.hurtTime > 0 && hasReceivedVelocity && attackCounter < attackCount.get()) {
                    val target = mc.pointedEntity?: return@handler
                    if (!p.isSprinting && smart.get() && getItemSpecificEnchantLevel(p.heldItemMainhand, Enchantments.KNOCKBACK) < 1) return@handler
                    p.attackTargetEntityWithCurrentItem(target)
                    attackCounter++
                } else if (p.hurtTime == 0 ) hasReceivedVelocity =false
            }
        }
        handler<PacketReceiveEvent> { e->
            val p = mc.player?: return@handler
            val pc = e.packet
            when (pc) {
                is SPacketEntityVelocity -> {
                    if (p.entityId == pc.entityID) {
                        when (mode.get()){
                            "Modify" -> {
                                when {
                                    h.get() == v.get() && h.get() == 0.0f -> e.cancel()
                                    h.get() == 0.0f -> {
                                        p.motionY = pc.realMotionY * v.get()
                                        e.cancel()
                                    }
                                    v.get() == 0.0f -> {
                                        p.motionX = pc.realMotionX * h.get()
                                        p.motionZ = pc.realMotionZ * h.get()
                                        e.cancel()
                                    }
                                    else -> {
                                        p.motionX = pc.realMotionX * h.get()
                                        p.motionY = pc.realMotionY * v.get()
                                        p.motionZ = pc.realMotionZ * h.get()
                                        e.cancel()
                                    }
                                }
                            }
                            else -> {
                                hasReceivedVelocity = true
                                attackCounter=0
                            }


                        }
                    }
                }
            }
        }
    }
}
