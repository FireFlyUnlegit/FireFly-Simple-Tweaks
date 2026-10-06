package dev.firefly.simpletweaks.util

import net.minecraft.block.ShapeContext
import net.minecraft.entity.Entity
import net.minecraft.particle.ParticleEffect
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.Vec3d
import net.minecraft.world.RaycastContext
import net.minecraft.world.World
import kotlin.math.cos
import kotlin.math.sin

/**
 * 1.21 port of the `World` extensions that 1.12.2 kept in `util/ParticleUtil.kt` (and in
 * `util/WorldExtensions.kt`). Only the helpers the currently ported handlers need are provided;
 * `spawnParticles` / `spawnCritParticlesServer` / `spawnCritParticlesClient` are not used by any
 * handler ported so far and are therefore not ported yet.
 *
 * `spawnRingParticles` is placed here rather than in a re-created `ParticleUtil.kt` because it is a
 * `World` extension, matching this tree's naming for world extensions.
 */

/**
 * 扩展函数：在指定位置生成一个环绕的粒子圆环
 * @param centerX, centerY, centerZ 圆心坐标
 * @param radius 环绕半径
 * @param count 粒子数量
 * @param angleOffset 角度偏移（传入随时间变化的数值可实现旋转）
 * @param yWave 粒子在Y轴上的波动幅度（让环看起来有立体的层次感）
 *
 * 1.12.2 → 1.21.1 mapping (both verified in the cached Yarn mappings):
 * | 1.12.2                                                             | 1.21.1                                                       |
 * |--------------------------------------------------------------------|--------------------------------------------------------------|
 * | `EnumParticleTypes`                                                | `net.minecraft.particle.ParticleEffect` (e.g. `ParticleTypes.CRIT`) |
 * | `WorldServer.spawnParticle(type, x, y, z, count, dx, dy, dz, speed)` | `ServerWorld.spawnParticles(effect, x, y, z, count, dx, dy, dz, speed)` (`method_14199`) |
 * | client `World.spawnParticle(type, x, y, z, vx, vy, vz)`             | `World.addParticle(effect, x, y, z, vx, vy, vz)`             |
 */
fun World.spawnRingParticles(
    particle: ParticleEffect,
    centerX: Double, centerY: Double, centerZ: Double,
    radius: Double,
    count: Int,
    angleOffset: Double = 0.0,
    yWave: Double = 0.0,
    speed: Double = 0.0
) {
    for (i in 0 until count) {
        val angle = angleOffset + i * (2 * Math.PI / count)
        val x = centerX + radius * cos(angle)
        val z = centerZ + radius * sin(angle)
        val y = centerY + if (yWave > 0) sin(angle * 2) * yWave else 0.0

        if (this is ServerWorld) {
            this.spawnParticles(particle, x, y, z, 1, 0.0, 0.0, 0.0, speed)
        } else {
            this.addParticle(particle, x, y, z, speed, speed, speed)
        }
    }
}

/**
 * 判断从起点到终点之间是否有方块阻挡（视线是否通畅）
 * @param start 起点
 * @param end 终点
 * @param stopOnLiquid 液体是否算阻挡
 * @param ignorePassable 是否忽略无碰撞箱方块（草、花、火把等）
 *
 * 1.12.2 → 1.21.1 mapping (verified against the cached Yarn mappings):
 * | 1.12.2                                                          | 1.21.1                                                        |
 * |-----------------------------------------------------------------|---------------------------------------------------------------|
 * | `World.rayTraceBlocks(start, end, stopOnLiquid, ignorePassable, false)` | `BlockView.raycast(RaycastContext)` (`method_17742`, a default method reachable from `World`) |
 * | `null` result means "nothing hit"                               | `BlockHitResult.getType() == HitResult.Type.MISS` (`method_17783`) |
 * | `ignorePassable = true` (skip blocks with no collision box)     | `RaycastContext.ShapeType.COLLIDER`; `false` maps to `OUTLINE` |
 * | `stopOnLiquid = false` / `true`                                 | `RaycastContext.FluidHandling.NONE` / `ANY`                   |
 *
 * `returnLastUncollidableBlock = false` (1.12.2's last argument) is what the `MISS` check above
 * already expresses, so no extra mapping is needed for it.
 */
fun World.hasLineOfSight(
    start: Vec3d,
    end: Vec3d,
    stopOnLiquid: Boolean = false,
    ignorePassable: Boolean = true,
): Boolean {
    val context = RaycastContext(
        start,
        end,
        if (ignorePassable) RaycastContext.ShapeType.COLLIDER else RaycastContext.ShapeType.OUTLINE,
        if (stopOnLiquid) RaycastContext.FluidHandling.ANY else RaycastContext.FluidHandling.NONE,
        ShapeContext.absent(),
    )
    return this.raycast(context).type == HitResult.Type.MISS
}

/**
 * 判断 from 实体能否看到 to 实体
 * @param fromEyeHeight from 实体的眼睛高度系数（默认 0.85，玩家大约是 0.85）
 * @param toCenterHeight to 实体的中心高度系数（默认 0.5，即实体高度的正中间）
 *
 * Only the `(Entity, Entity)` overload used by `EnchantKillAuraHandler` is ported; the 1.12.2
 * coordinate overloads (`hasLineOfSight(x1..z2)` / `canPositionSee`) have no caller in this batch.
 */
fun World.canEntitySee(
    from: Entity,
    to: Entity,
    fromEyeHeight: Double = 0.85,
    toCenterHeight: Double = 0.5,
    stopOnLiquid: Boolean = false,
    ignorePassable: Boolean = true,
): Boolean {
    val start = Vec3d(
        from.x,
        from.y + from.height * fromEyeHeight,
        from.z
    )
    val end = Vec3d(
        to.x,
        to.y + to.height * toCenterHeight,
        to.z
    )
    return hasLineOfSight(start, end, stopOnLiquid, ignorePassable)
}
