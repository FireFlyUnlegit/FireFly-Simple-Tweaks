package dev.firefly.simpletweaks.enchantments.handlers.mythic

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.mythic.EnchantStarfall
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.runPlayerAttack
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.projectile.EntityArrow
import net.minecraft.init.SoundEvents
import net.minecraft.util.DamageSource
import net.minecraft.util.EnumParticleTypes
import net.minecraft.util.SoundCategory
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import net.minecraft.world.WorldServer
import net.minecraftforge.event.entity.living.LivingHurtEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.util.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

object EnchantStarfallHandler : Listenable {

    private class FallingStar(
        val ownerUUID: UUID,
        var targetUUID: UUID,
        val dimension: Int,
        var x: Double, var y: Double, var z: Double,
        val damage: Float,
        val radius: Double,
        val trackingSpeed: Double,
        var life: Int = 0,
    )

    private val stars = mutableListOf<FallingStar>()

    @SubscribeEvent(priority = EventPriority.NORMAL)
    fun onArrowHit(e: LivingHurtEvent) {
        val arrow = e.source.immediateSource as? EntityArrow ?: return
        val shooter = arrow.shootingEntity as? EntityPlayer ?: return
        val target = e.entityLiving ?: return
        if (target === shooter) return

        val lvl = getItemSpecificEnchantLevel(shooter.heldItemMainhand, EnchantStarfall)
        if (lvl <= 0) return

        val count = when {
            lvl >= 5 -> 3
            lvl >= 3 -> 2
            else -> 1
        }
        val radius = 3.0 + 0.5 * lvl
        val baseDamage = e.amount * (1.5f + 0.5f * lvl)
        val trackingSpeed = 1.0 + 0.5 * lvl
        val world = shooter.world

        repeat(count) {
            val angle = Random.nextDouble() * Math.PI * 2.0
            val distance = 2.0 + Random.nextDouble() * 5.0
            val startX = target.posX + cos(angle) * distance
            val startZ = target.posZ + sin(angle) * distance
            val spawnY = findValidSpawnY(world, startX, startZ, target.posY + 15.0)

            stars.add(
                FallingStar(
                    ownerUUID = shooter.uniqueID,
                    targetUUID = target.uniqueID,
                    dimension = target.dimension,
                    x = startX, y = spawnY, z = startZ,
                    damage = baseDamage,
                    radius = radius,
                    trackingSpeed = trackingSpeed,
                )
            )
        }
    }

    private fun findValidSpawnY(world: World, x: Double, z: Double, startY: Double): Double {
        var y = startY.coerceAtMost(254.0)
        var checked = 0
        while (y > 1.0 && checked < 20) {
            val bp = BlockPos(x, y, z)
            val state = world.getBlockState(bp)
            if (!state.material.blocksMovement()) return y
            y -= 1.0
            checked++
        }
        return startY.coerceAtMost(254.0)
    }

    @SubscribeEvent
    fun onWorldTick(e: TickEvent.WorldTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        val world = e.world
        if (world.isRemote) return
        if (stars.isEmpty()) return

        val currentDim = world.provider.dimension

        val iter = stars.iterator()
        while (iter.hasNext()) {
            val star = iter.next()
            star.life++

            if (star.dimension != currentDim) continue

            var target: EntityLivingBase? = null
            for (entity in world.loadedEntityList) {
                if (entity is EntityLivingBase && entity.uniqueID == star.targetUUID) {
                    target = entity
                    break
                }
            }

            if (target != null && target.isEntityAlive) {
                val dx = target.posX - star.x
                val dy = (target.posY + target.height / 2.0) - star.y
                val dz = target.posZ - star.z
                val dist = sqrt(dx * dx + dy * dy + dz * dz)

                if (dist > 0.01) {
                    val step = minOf(star.trackingSpeed, dist)
                    star.x += dx / dist * step
                    star.y += dy / dist * step
                    star.z += dz / dist * step
                }
            } else {
                star.y -= 1.5
            }

            if (world is WorldServer) {
                world.spawnParticle(
                    EnumParticleTypes.FLAME,
                    star.x, star.y, star.z,
                    2, 0.1, 0.1, 0.1, 0.02,
                )
                world.spawnParticle(
                    EnumParticleTypes.SMOKE_NORMAL,
                    star.x, star.y, star.z,
                    1, 0.1, 0.1, 0.1, 0.01,
                )
            }

            var impact = false

            if (target != null && target.isEntityAlive) {
                val dist = target.getDistance(star.x, star.y, star.z)
                if (dist < 1.5) impact = true
            }

            if (star.y < 0.0) impact = true
            if (star.life > 100) impact = true

            if (impact) {
                explode(world, star)
                iter.remove()
            }
        }
    }

    private fun explode(world: World, star: FallingStar) {
        if (world is WorldServer) {
            world.spawnParticle(
                EnumParticleTypes.EXPLOSION_LARGE,
                star.x, star.y, star.z,
                1, 0.0, 0.0, 0.0, 0.0,
            )
            world.spawnParticle(
                EnumParticleTypes.FLAME,
                star.x, star.y, star.z,
                30, star.radius * 0.5, star.radius * 0.5, star.radius * 0.5, 0.1,
            )
            world.playSound(
                null, star.x, star.y, star.z,
                SoundEvents.ENTITY_GENERIC_EXPLODE,
                SoundCategory.PLAYERS,
                1.5f, 0.8f,
            )
        }

        val owner = world.getPlayerEntityByUUID(star.ownerUUID)
        val r2 = star.radius * star.radius

        val box = AxisAlignedBB(
            star.x - star.radius, star.y - star.radius, star.z - star.radius,
            star.x + star.radius, star.y + star.radius, star.z + star.radius,
        )
        val entities = world.getEntitiesWithinAABB(EntityLivingBase::class.java, box)

        for (entity in entities) {
            if (!entity.isEntityAlive) continue
            if (owner != null && entity === owner) continue
            if (owner != null && entity.isOnSameTeam(owner)) continue

            val dx = entity.posX - star.x
            val dy = entity.posY + entity.height / 2.0 - star.y
            val dz = entity.posZ - star.z
            val distSq = dx * dx + dy * dy + dz * dz
            if (distSq > r2) continue

            val dist = sqrt(distSq)
            val factor = 1.0f - (dist / star.radius).toFloat() * 0.5f
            val dmg = star.damage * factor

            if (owner != null) {
                entity.runPlayerAttack(
                    attacker = owner,
                    rawDamage = dmg,
                    forceHit = true,
                    source = DamageSource.causePlayerDamage(owner),
                    triggerEvent = true,
                    allowCrit = false,
                )
            } else {
                entity.attackEntityFrom(DamageSource.MAGIC, dmg)
            }
        }
    }
}