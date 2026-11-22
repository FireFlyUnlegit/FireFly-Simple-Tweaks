package dev.firefly.simpletweaks.util

import dev.firefly.simpletweaks.mixin.accessors.PersistentProjectileEntityAccessor
import net.minecraft.entity.projectile.PersistentProjectileEntity
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import java.util.Collections
import java.util.WeakHashMap

/**
 * Per-arrow flight model shared by `anti_drag` and `anti_gravity`.
 *
 * <p>Vanilla applies its own drag (`* 0.99`) and gravity (`- 0.05`, gated by `shouldFall()`) at the end
 * of `PersistentProjectileEntity#tick`; both are overwritten here at the end of the world tick, so the
 * arrow's next movement integrates this model instead. Owning the numbers is what lets one flight keep
 * its speed while another keeps its height without touching vanilla's tick.
 */
object ArrowBallistics {

    private const val DRAG = 0.99
    private const val GRAVITY = 0.05

    private class Shot(
        var model: Vec3d,
        var noDragUntil: Long,
        var noGravityUntil: Long,
        var lastTick: Long = Long.MIN_VALUE,
    )

    private val shots: MutableMap<PersistentProjectileEntity, Shot> =
        Collections.synchronizedMap(WeakHashMap())

    /**
     * Registers (or extends) an arrow's windows. `noDragTicks` / `noGravityTicks` of zero leave that
     * half of the model alone.
     */
    @JvmStatic
    fun attach(arrow: PersistentProjectileEntity, noDragTicks: Int, noGravityTicks: Int) {
        val now = arrow.world.time
        val shot = shots.getOrPut(arrow) { Shot(arrow.velocity, 0L, 0L) }
        if (noDragTicks > 0) shot.noDragUntil = maxOf(shot.noDragUntil, now + noDragTicks)
        if (noGravityTicks > 0) shot.noGravityUntil = maxOf(shot.noGravityUntil, now + noGravityTicks)
    }

    /** Advances every tracked arrow of [world] once; safe to call from more than one handler. */
    @JvmStatic
    fun tick(world: World) {
        if (world.isClient) return
        val now = world.time
        val entries = shots.entries.iterator()
        while (entries.hasNext()) {
            val entry = entries.next()
            val arrow = entry.key
            val shot = entry.value
            if (arrow.world !== world) continue
            if (arrow.isRemoved || (arrow as PersistentProjectileEntityAccessor).`simpletweaks$getInGround`() ||
                (now > shot.noDragUntil && now > shot.noGravityUntil)
            ) {
                entries.remove()
                continue
            }
            // Two handlers may tick the same world tick; only the first advances the model.
            if (shot.lastTick == now) continue
            shot.lastTick = now

            var next = shot.model
            if (now > shot.noDragUntil) next = next.multiply(DRAG)
            if (now > shot.noGravityUntil) next = next.add(0.0, -GRAVITY, 0.0)
            shot.model = next
            arrow.velocity = next
            arrow.velocityModified = true
        }
    }
}
