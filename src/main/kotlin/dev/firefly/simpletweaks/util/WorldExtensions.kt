package dev.firefly.simpletweaks.util

import net.minecraft.entity.Entity
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World

/**
 * 判断从起点到终点之间是否有方块阻挡（视线是否通畅）
 * @param start 起点
 * @param end 终点
 * @param stopOnLiquid 液体是否算阻挡
 * @param ignorePassable 是否忽略无碰撞箱方块（草、花、火把等）
 */
fun World.hasLineOfSight(
    start: Vec3d,
    end: Vec3d,
    stopOnLiquid: Boolean = false,
    ignorePassable: Boolean = true,
): Boolean {
    return rayTraceBlocks(start, end, stopOnLiquid, ignorePassable, false) == null
}

/**
 * 判断两个坐标之间是否有方块阻挡
 */
fun World.hasLineOfSight(
    x1: Double, y1: Double, z1: Double,
    x2: Double, y2: Double, z2: Double,
    stopOnLiquid: Boolean = false,
    ignorePassable: Boolean = true,
): Boolean {
    return hasLineOfSight(
        Vec3d(x1, y1, z1),
        Vec3d(x2, y2, z2),
        stopOnLiquid, ignorePassable
    )
}

/**
 * 判断 from 实体能否看到 to 实体
 * @param fromEyeHeight from 实体的眼睛高度系数（默认 0.85，玩家大约是 0.85）
 * @param toCenterHeight to 实体的中心高度系数（默认 0.5，即实体高度的正中间）
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
        from.posX,
        from.posY + from.height * fromEyeHeight,
        from.posZ
    )
    val end = Vec3d(
        to.posX,
        to.posY + to.height * toCenterHeight,
        to.posZ
    )
    return hasLineOfSight(start, end, stopOnLiquid, ignorePassable)
}

/**
 * 判断某个位置能否看到某个实体
 */
fun World.canPositionSee(
    x: Double, y: Double, z: Double,
    to: Entity,
    toCenterHeight: Double = 0.5,
    stopOnLiquid: Boolean = false,
    ignorePassable: Boolean = true,
): Boolean {
    val start = Vec3d(x, y, z)
    val end = Vec3d(
        to.posX,
        to.posY + to.height * toCenterHeight,
        to.posZ
    )
    return hasLineOfSight(start, end, stopOnLiquid, ignorePassable)
}