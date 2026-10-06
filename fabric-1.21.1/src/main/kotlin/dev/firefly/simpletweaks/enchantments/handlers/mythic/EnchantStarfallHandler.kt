package dev.firefly.simpletweaks.enchantments.handlers.mythic

import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.compat.WorldSide
import dev.firefly.simpletweaks.compat.event.EventPriority
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.event.TickEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.ModEnchantmentKeys
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.runPlayerAttack
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.passive.TameableEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.projectile.PersistentProjectileEntity
import net.minecraft.particle.ParticleTypes
import net.minecraft.registry.RegistryKey
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.world.World
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 1.21 port of `enchantments/handlers/mythic/EnchantStarfallHandler.kt`.
 *
 * Line-by-line mapping from 1.12.2:
 * | 1.12.2                                                        | 1.21.1                                                                 |
 * |---------------------------------------------------------------|------------------------------------------------------------------------|
 * | `net.minecraftforge...LivingHurtEvent`                        | `compat.event.LivingHurtEvent`                                          |
 * | `net.minecraftforge...gameevent.TickEvent`                    | `compat.event.TickEvent`                                                |
 * | `net.minecraftforge...EventPriority`                          | `compat.event.EventPriority`                                            |
 * | `EntityLivingBase` / `EntityPlayer`                           | `net.minecraft.entity.LivingEntity` / `net.minecraft.entity.player.PlayerEntity` |
 * | `e.source.immediateSource`                                    | `e.source.source` (Yarn `DamageSource.getSource`, method_5509)           |
 * | `EntityArrow`                                                 | `net.minecraft.entity.projectile.PersistentProjectileEntity` (the 1.21 common base of `ArrowEntity` / `SpectralArrowEntity`) |
 * | `arrow.shootingEntity`                                        | `arrow.owner` (Yarn `ProjectileEntity.getOwner`, method_24921)           |
 * | `shooter.heldItemMainhand` / `shooter.uniqueID`                | `shooter.mainHandStack` / `shooter.uuid`                                |
 * | `target.posX/posY/posZ` / `target.height`                     | `target.x/y/z` / `target.height` (`method_17682`)                        |
 * | `target.dimension` (int)                                      | `target.world.registryKey` (`World.getRegistryKey`, method_27983)        |
 * | `world.provider.dimension`                                    | `world.registryKey`                                                     |
 * | `world.isRemote`                                              | `WorldSide.isClient(world)` (field_9236 is a FIELD)                     |
 * | `BlockPos(x, y, z)` with doubles                              | `BlockPos.ofFloored(x, y, z)` (`method_49637`; 1.12.2's double constructor floored too) |
 * | `state.material.blocksMovement()`                             | `state.blocksMovement()` (`AbstractBlockState`, method_51366)            |
 * | `world.loadedEntityList`                                      | `ServerWorld.iterateEntities()` (`method_27909`; the client case returned earlier) |
 * | `target.getDistance(x, y, z)`                                 | `sqrt(target.squaredDistanceTo(x, y, z))` (`method_5649`; 1.12.2's `getDistance` was exactly that) |
 * | `AxisAlignedBB(x1, y1, z1, x2, y2, z2)`                       | `net.minecraft.util.math.Box(x1, y1, z1, x2, y2, z2)`                   |
 * | `world.getEntitiesWithinAABB(EntityLivingBase::class.java, box)` | `world.getEntitiesByClass(LivingEntity::class.java, box) { true }` (`method_8390`) |
 * | `world.getPlayerEntityByUUID(uuid)`                           | `world.getPlayerByUuid(uuid)` (`method_18470`)                          |
 * | `entity.isOnSameTeam(owner)`                                  | `entity.isTeammate(owner)` (`method_5722`)                              |
 * | `entity.isEntityAlive` / `entity.posX/posY/posZ`              | `entity.isAlive` / `entity.x/y/z`                                       |
 * | `EnumParticleTypes.FLAME` / `SMOKE_NORMAL` / `EXPLOSION_LARGE` | `ParticleTypes.FLAME` / `ParticleTypes.SMOKE` / `ParticleTypes.EXPLOSION` |
 * | `SoundEvents.ENTITY_GENERIC_EXPLODE`                          | unchanged name — but it is a `RegistryEntry<SoundEvent>` in 1.21, so `World.playSound` resolves to the `RegistryEntry` overload |
 * | `DamageSource.causePlayerDamage(owner)`                       | `owner.damageSources.playerAttack(owner)` (`method_48802`)               |
 * | `DamageSource.MAGIC`                                          | `entity.damageSources.magic()` (`method_48831`)                         |
 * | `entity.attackEntityFrom(source, dmg)`                        | `entity.damage(source, dmg)` (`method_5643`)                            |
 * | `EnchantStarfall` (Enchantment object)                        | `ModEnchantmentKeys.STARFALL` (RegistryKey)                             |
 *
 * No `ArrowLooseEvent` is used: the enchantment fires on arrow **hit**, exactly as in 1.12.2.
 * The whole state machine (2-7 block random spawn ring, 1/2/3 stars by level, spawn-height probe,
 * per-tick homing by target UUID, 1.5-block contact radius, 100-tick lifetime, distance falloff
 * `1 - 0.5 * d / r`) is carried over verbatim, including the shared `stars` list and the
 * `iter`-based removal inside the world tick.
 *
 * <h2>⚠️ The one thing that could NOT be carried over verbatim: y = 0 is not the world floor</h2>
 * 1.12.2's overworld started at y=0, so two literals in this file silently doubled as "the void":
 * `if (star.y &lt; 0.0) impact = true` and `while (y &gt; 1.0)` in [findValidSpawnY]. 1.21.1's overworld
 * floor is **y=-64**, which turns the first one into "detonate on the first tick anywhere below
 * y=0" — the star explodes at its spawn point, `target.y + 15`, i.e. ~15 blocks above the target and
 * outside its own radius, so it deals no damage to anything.
 *
 * <p>Confirmed from a real session before the fix, not inferred: of 90 `star-impact` log lines,
 * **90 had `life=1`** and every `starY` was negative (-48.45..-46). Both literals now read the
 * dimension's actual floor via `world.bottomY`.
 *
 * <p>Note the failure was invisible on the surface (y&gt;0), which is why it survived the earlier
 * acceptance round: it only manifests at or below y=0.
 */
object EnchantStarfallHandler : Listenable {

    private class FallingStar(
        val ownerUUID: UUID,
        var targetUUID: UUID,
        val dimension: RegistryKey<World>,
        var x: Double, var y: Double, var z: Double,
        val damage: Float,
        val radius: Double,
        val trackingSpeed: Double,
        var life: Int = 0,
    )

    private val stars = mutableListOf<FallingStar>()

    @SubscribeEvent(priority = EventPriority.NORMAL)
    fun onArrowHit(e: LivingHurtEvent) {
        val arrow = e.source.source as? PersistentProjectileEntity ?: return
        val shooter = arrow.owner as? PlayerEntity ?: return
        val target = e.entityLiving ?: return
        if (target === shooter) return

        val lvl = getItemSpecificEnchantLevel(shooter.mainHandStack, ModEnchantmentKeys.STARFALL)
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
            val startX = target.x + cos(angle) * distance
            val startZ = target.z + sin(angle) * distance
            val spawnY = findValidSpawnY(world, startX, startZ, target.y + 15.0)

            stars.add(
                FallingStar(
                    ownerUUID = shooter.uuid,
                    targetUUID = target.uuid,
                    dimension = target.world.registryKey,
                    x = startX, y = spawnY, z = startZ,
                    damage = baseDamage,
                    radius = radius,
                    trackingSpeed = trackingSpeed,
                )
            )
        }
        STLog.log("Starfall") {
            "shooter=${shooter.name.string}, target=${target.name.string}, lvl=$lvl, stars=$count, radius=$radius, " +
                "hitDamage=${e.amount}, starDamage=$baseDamage, trackingSpeed=$trackingSpeed, outcome=stars-spawned"
        }
    }

    private fun findValidSpawnY(world: World, x: Double, z: Double, startY: Double): Double {
        // 254.0 is still 1.12.2's cap (its build limit was 256); it only matters for a target above
        // y=239, so it is left alone rather than re-derived from 1.21's 320.
        var y = startY.coerceAtMost(254.0)
        var checked = 0
        // ⚠️ 1.12.2 wrote `y > 1.0` because its world floor was y=0. 1.21.1's overworld floor is
        // y=-64, so the literal 1.0 made the probe refuse to run at all below y=1 — see the sibling
        // note on the `star.y < 0.0` impact check, which is the same bug in a more visible place.
        while (y > world.bottomY.toDouble() && checked < 20) {
            val bp = BlockPos.ofFloored(x, y, z)
            val state = world.getBlockState(bp)
            if (!state.blocksMovement()) return y
            y -= 1.0
            checked++
        }
        return startY.coerceAtMost(254.0)
    }

    @SubscribeEvent
    fun onWorldTick(e: TickEvent.WorldTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        val world = e.world
        if (WorldSide.isClient(world)) return
        if (stars.isEmpty()) return

        val currentDim = world.registryKey

        val iter = stars.iterator()
        while (iter.hasNext()) {
            val star = iter.next()
            star.life++

            if (star.dimension != currentDim) continue

            var target: LivingEntity? = null
            // 1.12.2 iterated `WorldServer.loadedEntityList`; `iterateEntities()` is the 1.21
            // equivalent for a server world (the client case returned at the top of this method).
            if (world is ServerWorld) {
                for (entity in world.iterateEntities()) {
                    if (entity is LivingEntity && entity.uuid == star.targetUUID) {
                        target = entity
                        break
                    }
                }
            }

            if (target != null && target.isAlive) {
                val dx = target.x - star.x
                val dy = (target.y + target.height / 2.0) - star.y
                val dz = target.z - star.z
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

            if (world is ServerWorld) {
                world.spawnParticles(
                    ParticleTypes.FLAME,
                    star.x, star.y, star.z,
                    2, 0.1, 0.1, 0.1, 0.02,
                )
                world.spawnParticles(
                    ParticleTypes.SMOKE,
                    star.x, star.y, star.z,
                    1, 0.1, 0.1, 0.1, 0.01,
                )
            }

            var impact = false

            if (target != null && target.isAlive) {
                val dist = sqrt(target.squaredDistanceTo(star.x, star.y, star.z))
                if (dist < 1.5) impact = true
            }

            if (star.y < world.bottomY.toDouble()) impact = true
            if (star.life > 100) impact = true

            if (impact) {
                explode(world, star)
                iter.remove()
                STLog.log("Starfall") {
                    "owner=${star.ownerUUID}, target=${star.targetUUID}, starX=${star.x}, starY=${star.y}, " +
                        "starZ=${star.z}, life=${star.life}, damage=${star.damage}, radius=${star.radius}, " +
                        "outcome=star-impact"
                }
            }
        }
    }

    private fun explode(world: World, star: FallingStar) {
        if (world is ServerWorld) {
            world.spawnParticles(
                ParticleTypes.EXPLOSION,
                star.x, star.y, star.z,
                1, 0.0, 0.0, 0.0, 0.0,
            )
            world.spawnParticles(
                ParticleTypes.FLAME,
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

        val owner = world.getPlayerByUuid(star.ownerUUID)
        val r2 = star.radius * star.radius

        val box = Box(
            star.x - star.radius, star.y - star.radius, star.z - star.radius,
            star.x + star.radius, star.y + star.radius, star.z + star.radius,
        )
        val entities = world.getEntitiesByClass(LivingEntity::class.java, box) { true }

        for (entity in entities) {
            if (!entity.isAlive) continue
            if (owner != null && entity === owner) continue
            if (owner != null && entity.isTeammate(owner)) continue
            // 1.21's `isTeammate` is scoreboard-team-only. 1.12.2's `EntityLivingBase#isOnSameTeam`
            // additionally returned true when `this` was a tamed mob owned by the other entity, so a
            // tamed wolf used to be spared. Restore that clause explicitly; `isOwner` is
            // TameableEntity#isOwner(LivingEntity) = method_6171.
            if (owner != null && entity is TameableEntity && entity.isOwner(owner)) continue

            val dx = entity.x - star.x
            val dy = entity.y + entity.height / 2.0 - star.y
            val dz = entity.z - star.z
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
                    source = owner.damageSources.playerAttack(owner),
                    triggerEvent = true,
                    allowCrit = false,
                )
            } else {
                entity.damage(entity.damageSources.magic(), dmg)
            }
        }
    }
}
