package dev.firefly.simplemod.core.event

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiScreen
import net.minecraft.entity.Entity
import net.minecraft.network.Packet

object EventDispatcher {
    private val mc = Minecraft.getMinecraft()
    private var lastSecondTick = 0L

    fun onTick() { EventManager.call(TickEvent()) }

    fun onPlayerUpdate() { EventManager.call(PlayerUpdateEvent()) }

    fun onMotionUpdate(x: Double, y: Double, z: Double, onGround: Boolean, pre: Boolean) {
        EventManager.call(MotionEvent(x, y, z, onGround, if (pre) EventState.PRE else EventState.POST))
    }

    fun onJump(): Boolean {
        val event = JumpEvent()
        EventManager.call(event)
        return !event.isCancelled
    }

    fun onAttack(target: Entity?) { EventManager.call(AttackEvent(target)) }

    fun onSprint(sprinting: Boolean) { EventManager.call(SprintEvent(sprinting)) }

    fun onStep(stepHeight: Float): Float {
        val event = StepEvent(stepHeight)
        EventManager.call(event)
        return if (event.isCancelled) 0f else event.stepHeight
    }

    fun onRender2D(partialTicks: Float) { EventManager.call(Render2DEvent(partialTicks)) }
    fun onRender3D(partialTicks: Float) { EventManager.call(Render3DEvent(partialTicks)) }

    fun onPacketSend(packet: Packet<*>): Boolean {
        val event = PacketSendEvent(packet)
        EventManager.call(event)
        return !event.isCancelled
    }

    fun onPacketReceive(packet: Packet<*>): Boolean {
        val event = PacketReceiveEvent(packet)
        EventManager.call(event)
        return !event.isCancelled
    }

    fun onGuiOpen(screen: Any?): Boolean {
        val event = GuiOpenEvent(screen as? GuiScreen)
        EventManager.call(event)
        return !event.isCancelled
    }

    fun onWorldChange() { EventManager.call(WorldChangeEvent()) }
}