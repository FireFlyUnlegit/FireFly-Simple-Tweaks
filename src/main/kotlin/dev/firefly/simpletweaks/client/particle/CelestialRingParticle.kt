package dev.firefly.simpletweaks.client.particle

import net.minecraft.client.particle.ParticleSimpleAnimated
import net.minecraft.world.World
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

class CelestialRingParticle(
    world: World,
    private val ownerUUID: UUID,
    private val angleOffset: Double,
    private val radius: Double,
    private val yOffset: Double,
    private val rotateSpeed: Double,
) : ParticleSimpleAnimated(world, 0.0, 0.0, 0.0, 176, 8, 0.0F) {

    private var spawned = false

    init {
        this.particleScale *= 0.7F
        this.particleMaxAge = 40 + this.rand.nextInt(8)
        this.canCollide = false
        setColorFade(0xFFD700)
    }

    override fun onUpdate() {
        if (!spawned) {
            spawned = true
            val player = world.getPlayerEntityByUUID(ownerUUID)
            if (player != null) {
                val time = player.ticksExisted.toDouble()
                val angle = angleOffset + time * rotateSpeed
                this.posX = player.posX + radius * cos(angle)
                this.posY = player.posY + yOffset + sin(time * 0.1) * 0.15
                this.posZ = player.posZ + radius * sin(angle)
                this.prevPosX = this.posX
                this.prevPosY = this.posY
                this.prevPosZ = this.posZ
            }
            return
        }

        this.prevPosX = this.posX
        this.prevPosY = this.posY
        this.prevPosZ = this.posZ

        if (this.particleAge++ >= this.particleMaxAge) {
            this.setExpired()
            return
        }

        if (this.particleAge > this.particleMaxAge / 2) {
            val fadeProgress = (this.particleAge - this.particleMaxAge / 2).toFloat() / (this.particleMaxAge / 2).toFloat()
            this.setAlphaF(1.0F - fadeProgress)
        }

        val frame = 8 - 1 - this.particleAge * 8 / this.particleMaxAge
        setParticleTextureIndex(176 + frame.coerceIn(0, 7))

        val player = world.getPlayerEntityByUUID(ownerUUID)
        if (player == null || !player.isEntityAlive) {
            this.setExpired()
            return
        }

        val time = player.ticksExisted.toDouble()
        val angle = angleOffset + time * rotateSpeed
        this.posX = player.posX + radius * cos(angle)
        this.posY = player.posY + yOffset + sin(time * 0.1) * 0.15
        this.posZ = player.posZ + radius * sin(angle)
    }

    override fun getFXLayer(): Int = 0
}