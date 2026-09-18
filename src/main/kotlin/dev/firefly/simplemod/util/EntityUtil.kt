package dev.firefly.simplemod.util

import net.minecraft.entity.Entity
import net.minecraft.util.math.MathHelper.sqrt
import net.minecraft.util.math.Vec3d

fun relativeSpeed(a: Entity, b: Entity, includeY: Boolean = false): Double {
    val diff = Vec3d(a.motionX, if (includeY) a.motionY else 0.0, a.motionZ)
        .subtract(Vec3d(b.motionX, if (includeY) b.motionY else 0.0, b.motionZ))
    return sqrt(diff.x * diff.x + diff.y * diff.y + diff.z * diff.z).toDouble()
}