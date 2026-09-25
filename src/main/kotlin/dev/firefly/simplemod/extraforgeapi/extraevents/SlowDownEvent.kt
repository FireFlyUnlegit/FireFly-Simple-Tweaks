package dev.firefly.simplemod.extraforgeapi.extraevents

import net.minecraft.entity.player.EntityPlayer
import net.minecraftforge.fml.common.eventhandler.Event

class SlowDownEvent(
    val player: EntityPlayer,
    val type: Type,
    var forward: Float,
    var strafe: Float,
    val factor: Float
) : Event() {

    val originalForward: Float = forward
    val originalStrafe: Float = strafe

    var skipSlowDown: Boolean = false

    val isModified: Boolean
        get() = forward != originalForward || strafe != originalStrafe || skipSlowDown

    /**
     * 设置减速后的目标速度因子。
     */
    var speedFactor: Float
        get() = forward / originalForward.coerceAtLeast(0.0001f)
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            val ratio = clamped / factor
            forward = originalForward * ratio
            strafe  = originalStrafe * ratio
            if (clamped >= 1f) skipSlowDown = true
        }

    enum class Type { SNEAK, BLOCKING, EATING, BOW }
}