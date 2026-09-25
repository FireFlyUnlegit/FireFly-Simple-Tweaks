package dev.firefly.simplemod.util

import net.minecraft.util.EnumParticleTypes
import net.minecraft.world.World
import net.minecraft.world.WorldServer
import kotlin.math.cos
import kotlin.math.sin

/**
 * 扩展函数：在指定位置生成一批带随机扩散的粒子
 * 注意：服务端批量生成时，只能传入一个统一的速度值
 */
fun World.spawnParticles(
    particle: EnumParticleTypes,
    x: Double, y: Double, z: Double,
    count: Int,
    spreadX: Double, spreadY: Double, spreadZ: Double,
    speed: Double = 0.0
) {
    if (this is WorldServer) {
        this.spawnParticle(particle, x, y, z, count, spreadX, spreadY, spreadZ, speed)
    } else {
        for (i in 0 until count) {
            this.spawnParticle(
                particle,
                x + (this.rand.nextDouble() - 0.5) * spreadX * 2,
                y + (this.rand.nextDouble() - 0.5) * spreadY * 2,
                z + (this.rand.nextDouble() - 0.5) * spreadZ * 2,
                speed, speed, speed
            )
        }
    }
}
/**
 * 扩展函数：在指定位置生成一个环绕的粒子圆环
 * @param centerX, centerY, centerZ 圆心坐标
 * @param radius 环绕半径
 * @param count 粒子数量
 * @param angleOffset 角度偏移（传入随时间变化的数值可实现旋转）
 * @param yWave 粒子在Y轴上的波动幅度（让环看起来有立体的层次感）
 */
fun World.spawnRingParticles(
    particle: EnumParticleTypes,
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

        if (this is WorldServer) {
            this.spawnParticle(particle, x, y, z, 1, 0.0, 0.0, 0.0, speed)
        } else {
            this.spawnParticle(particle, x, y, z, speed, speed, speed)
        }
    }
}