package dev.firefly.simplemod.core.event

import net.minecraft.client.gui.GuiScreen
import net.minecraft.entity.Entity
import net.minecraft.network.Packet

abstract class Event
abstract class CancellableEvent : Event() {
    var isCancelled = false
    fun cancel() { isCancelled = true }
}

enum class EventState { PRE, POST }

// 玩家更新 - 最常用
class PlayerUpdateEvent : Event()

// 玩家移动事件
class MotionEvent(
    var x: Double,
    var y: Double,
    var z: Double,
    var onGround: Boolean,
    val state: EventState
) : Event()

// 攻击事件
class AttackEvent(val target: Entity?) : Event()

// 跳跃事件
class JumpEvent : CancellableEvent()

// 步进（台阶）事件
class StepEvent(var stepHeight: Float) : CancellableEvent()

// 疾跑状态变化
class SprintEvent(val sprinting: Boolean) : Event()

// Tick 事件
class TickEvent : Event()

// 2D 渲染 (HUD)
class Render2DEvent(val partialTicks: Float) : Event()

// 3D 渲染 (世界)
class Render3DEvent(val partialTicks: Float) : Event()

// 网络事件
class PacketSendEvent(val packet: Packet<*>) : CancellableEvent()
class PacketReceiveEvent(val packet: Packet<*>) : CancellableEvent()

// GUI 事件
class GuiOpenEvent(val screen: GuiScreen?) : CancellableEvent()

// 世界切换
class WorldChangeEvent : Event()