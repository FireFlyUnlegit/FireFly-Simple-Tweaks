package dev.firefly.simplemod.util

import net.minecraft.network.play.server.SPacketEntityVelocity

val SPacketEntityVelocity.realMotionX: Double
    get() = this.motionX / 8000.0
val SPacketEntityVelocity.realMotionY: Double
    get() = this.motionY / 8000.0
val SPacketEntityVelocity.realMotionZ: Double
    get() = this.motionZ / 8000.0