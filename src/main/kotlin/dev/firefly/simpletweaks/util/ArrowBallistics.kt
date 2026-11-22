package dev.firefly.simpletweaks.util

import dev.firefly.simpletweaks.mixin.accessors.PersistentProjectileEntityAccessor
import net.minecraft.entity.projectile.PersistentProjectileEntity
import net.minecraft.world.World
import java.util.Collections
import java.util.WeakHashMap

/**
 * Per-arrow windows for `anti_drag` and `anti_gravity`.
 *
 * <p>This works as a **delta on whatever the arrow's velocity already is**, not as a stored trajectory:
 * vanilla applies drag (`* 0.99`) and gravity (`- 0.05`, gated by `shouldFall()`) at the end of
 * `PersistentProjectileEntity#tick`, and other enchantments (tracking arrow) steer by rewriting the
 * velocity. Replaying a saved launch vector would overwrite that steering, so instead the drag/gravity
 * terms are undone on the current vector — anything another seam did stays intact.
 */
object ArrowBallistics {

    private const val DRAG = 0.99
    private const val GRAVITY = 0.05

    private class Window(var noDragUntil: Long, var noGravityUntil: Long, var lastTick: Long = Long.MIN_VALUE)

    private val windows: MutableMap<PersistentProjectileEntity, Window> =
        Collections.synchronizedMap(WeakHashMap())

    /**
     * Registers (or extends) an arrow's windows. `noDragTicks` / `noGravityTicks` of zero leave that
     * half of the physics alone.
     */
    @JvmStatic
    fun attach(arrow: PersistentProjectileEntity, noDragTicks: Int, noGravityTicks: Int) {
        val now = arrow.world.time
        val window = windows.getOrPut(arrow) { Window(0L, 0L) }
        if (noDragTicks > 0) window.noDragUntil = maxOf(window.noDragUntil, now + noDragTicks)
        if (noGravityTicks > 0) window.noGravityUntil = maxOf(window.noGravityUntil, now + noGravityTicks)
    }

    /** Applies the undo pass once per world tick; safe to call from more than one handler. */
    @JvmStatic
    fun tick(world: World) {
        if (world.isClient) return
        val now = world.time
        val entries = windows.entries.iterator()
        while (entries.hasNext()) {
            val entry = entries.next()
            val arrow = entry.key
            val window = entry.value
            if (arrow.world !== world) continue
            val noDrag = now <= window.noDragUntil
            val noGravity = now <= window.noGravityUntil
            val inGround = (arrow as PersistentProjectileEntityAccessor).`simpletweaks$getInGround`()
            // Landed, dead, expired, or in water (buoyancy is not air drag — undoing it would launch the
            // arrow out of the water) means the vanilla tick is left alone from here on.
            if (arrow.isRemoved || inGround || (!noDrag && !noGravity) || arrow.isTouchingWater) {
                if (arrow.isRemoved || inGround || (!noDrag && !noGravity)) entries.remove()
                continue
            }
            if (window.lastTick == now) continue
            window.lastTick = now

            var velocity = arrow.velocity
            if (noDrag) velocity = velocity.multiply(1.0 / DRAG)
            if (noGravity) velocity = velocity.add(0.0, GRAVITY, 0.0)
            arrow.velocity = velocity
            arrow.velocityModified = true
        }
    }
}
