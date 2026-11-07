package dev.firefly.simpletweaks.util

import net.minecraft.entity.Entity
import net.minecraft.util.math.Vec3d
import kotlin.math.sqrt

/**
 * 1.21 port of the `relativeSpeed` half of `util/EntityUtil.kt`.
 *
 * `motionX/motionY/motionZ` no longer exist as fields; 1.21 exposes the velocity as a single
 * [Vec3d] via `Entity.getVelocity()` (`method_18798`). The original zeroed Y unless `includeY`, so
 * the same shape is preserved.
 */
fun relativeSpeed(a: Entity, b: Entity, includeY: Boolean = false): Double {
    val va = a.velocity
    val vb = b.velocity
    val diff = Vec3d(va.x, if (includeY) va.y else 0.0, va.z)
        .subtract(Vec3d(vb.x, if (includeY) vb.y else 0.0, vb.z))
    return sqrt(diff.x * diff.x + diff.y * diff.y + diff.z * diff.z)
}

/**
 * 1.21 port of the `fireTime` extension.
 *
 * The 1.12.2 version needed a mixin accessor (`EntityFireTimeAccessor`) because the field was
 * private. In 1.21 `Entity.getFireTicks()`/`setFireTicks(int)` are **public**
 * (`method_20802` / `method_20803`), so the accessor mixin is deleted outright.
 */
var Entity.fireTime: Int
    get() = this.fireTicks
    set(value) {
        this.fireTicks = value
    }
