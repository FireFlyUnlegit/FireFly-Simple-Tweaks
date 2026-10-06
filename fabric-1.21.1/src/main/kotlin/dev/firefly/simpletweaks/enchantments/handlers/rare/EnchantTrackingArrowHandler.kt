package dev.firefly.simpletweaks.enchantments.handlers.rare

import dev.firefly.simpletweaks.compat.event.EntityJoinWorldEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.event.TickEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.ModEnchantmentKeys
import dev.firefly.simpletweaks.interfaces.SimpleTweaksArrow
import dev.firefly.simpletweaks.mixin.PersistentProjectileEntityAccessor
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.projectile.PersistentProjectileEntity
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.Vec3d
import kotlin.math.acos

/**
 * `tracking_arrow`: arrows home in on nearby mobs, turning a limited number of degrees per tick.
 *
 * <h2>Port notes</h2>
 * | 1.12.2 | 1.21.1 |
 * |---|---|
 * | `EntityJoinWorldEvent` marks the arrow with `st_tracking_level` NBT | same seam, [SimpleTweaksArrow] field |
 * | `world.loadedEntityList` | `ServerWorld#iterateEntities()` |
 * | `EntityArrowAccessor.inGround` | `PersistentProjectileEntityAccessor` |
 * | `arrow.motionX/Y/Z = ...; velocityChanged = true` | `setVelocity(Vec3d)`; `velocityModified = true` |
 * | `getDistanceSq(entity)` | `squaredDistanceTo(entity)` |
 * | `isOnSameTeam(shooter)` | `isTeammate(shooter)` |
 * | `entityBoundingBox.grow(r)` | `getBoundingBox().expand(r)` |
 *
 * <p>The steering maths is unchanged: the turn is capped at `15 + level * 15` degrees per tick, the
 * direction is interpolated along the great-circle fraction when the target is outside that cone
 * (`curDir * (1 - t) + targetDir * t`, re-normalised), and the resulting velocity keeps the arrow's
 * speed scaled by the 0.99 decay factor.
 *
 * <p>Behaviour that is deliberately **not** simplified: this runs on the **logical server only**
 * (`world.isClient` returns early), so it is unaffected by the fact that `EntityJoinWorldEvent` in this
 * port has no client-side hook.
 *
 * <h2>Known deviation from 1.12.2</h2>
 * 1.12.2 guarded with `arrow.ticksExisted > 200`; the equivalent here is `arrow.age > 200`, which
 * matches. It did **not** skip arrows whose owner has since logged out or died — neither does this.
 *
 * <p>The piercing interaction is ported as-is: a tracking arrow that is also a piercing arrow prefers
 * targets it has not hit yet, and only falls back to targets it can still hit (below `maxPerTarget`),
 * which is why the per-target hit map lives on the projectile rather than in this handler.
 */
object EnchantTrackingArrowHandler : Listenable {

    @SubscribeEvent
    fun onEntityJoin(e: EntityJoinWorldEvent) {
        if (e.world.isClient) return
        val arrow = e.entity as? PersistentProjectileEntity ?: return
        val shooter = arrow.owner as? PlayerEntity ?: return
        val lvl = getItemSpecificEnchantLevel(shooter.mainHandStack, ModEnchantmentKeys.TRACKING_ARROW)
        if (lvl <= 0) return
        (arrow as SimpleTweaksArrow).`simpletweaks$setTrackingLevel`(lvl)
    }

    @SubscribeEvent
    fun onWorldTick(e: TickEvent.WorldTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        val world = e.world
        if (world.isClient) return
        val serverWorld = world as? ServerWorld ?: return

        for (entity in serverWorld.iterateEntities()) {
            val arrow = entity as? PersistentProjectileEntity ?: continue
            val state = arrow as SimpleTweaksArrow

            val lvl = state.`simpletweaks$getTrackingLevel`()
            if (lvl <= 0) continue
            if ((arrow as PersistentProjectileEntityAccessor).`simpletweaks$getInGround`()) continue
            if (arrow.isRemoved) continue
            if (arrow.age > 200) continue

            val shooter = arrow.owner as? PlayerEntity ?: continue

            val isPiercing = state.`simpletweaks$isPiercing`()
            val hits: Map<Int, Int> = if (isPiercing) state.`simpletweaks$getPierceHits`() else emptyMap()
            val maxPerTarget = if (isPiercing) state.`simpletweaks$getMaxPerTarget`() else 0

            val range = 2.0 + lvl
            val candidates = world
                .getNonSpectatingEntities(LivingEntity::class.java, arrow.boundingBox.expand(range))
                .filter {
                    it !== shooter && it.isAlive && !it.isTeammate(shooter) &&
                        (!isPiercing || (hits[it.id] ?: 0) < maxPerTarget)
                }

            val target = if (isPiercing) {
                // Prefer a target this arrow has not hit yet; only reuse one it may still hit.
                candidates.filter { (hits[it.id] ?: 0) == 0 }.minByOrNull { it.squaredDistanceTo(arrow) }
                    ?: candidates.filter { (hits[it.id] ?: 0) > 0 }.minByOrNull { it.squaredDistanceTo(arrow) }
            } else {
                candidates.minByOrNull { it.squaredDistanceTo(arrow) }
            }
            if (target == null) continue

            val curVel = arrow.velocity
            val speed = curVel.length()
            if (speed < 0.5) continue

            val toTarget = Vec3d(
                target.x - arrow.x,
                target.y + target.height / 2.0 - arrow.y,
                target.z - arrow.z,
            )
            if (toTarget.lengthSquared() < 0.01) continue

            val curDir = curVel.normalize()
            val targetDir = toTarget.normalize()

            val dot = (curDir.x * targetDir.x + curDir.y * targetDir.y + curDir.z * targetDir.z)
                .coerceIn(-1.0, 1.0)
            val angle = acos(dot)
            val maxRadPerTick = Math.toRadians(15.0 + lvl * 15.0)

            val newDir = if (angle <= maxRadPerTick) {
                targetDir
            } else {
                val t = maxRadPerTick / angle
                curDir.multiply(1.0 - t).add(targetDir.multiply(t)).normalize()
            }

            val decay = 0.99
            arrow.velocity = newDir.multiply(speed * decay)
            arrow.velocityModified = true

            if (arrow.age % 2 == 0) {
                serverWorld.spawnParticles(
                    ParticleTypes.END_ROD,
                    arrow.x, arrow.y, arrow.z,
                    1, 0.0, 0.0, 0.0, 0.0,
                )
            }
        }
    }
}
