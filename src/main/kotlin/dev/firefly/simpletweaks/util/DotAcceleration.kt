package dev.firefly.simpletweaks.util

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.util.damagesource.damageElemental
import dev.firefly.simpletweaks.util.elements.ElementType
import net.minecraft.entity.LivingEntity
import java.lang.ref.WeakReference
import java.util.UUID

/**
 * Damage-over-time acceleration behind `catalyze`.
 *
 * Only the interval shrinks: every DoT keeps its own per-tick value, which is why venom asks
 * [factorOf] instead of scaling damage. Burning has no mod-side tick to shorten, so [tick] pays the
 * difference as extra fire damage on vanilla's own 20-tick cadence.
 */
object DotAcceleration {

    /** Vanilla burns once per 20 ticks. */
    private const val FIRE_INTERVAL = 20

    /**
     * Accumulating cadence for a DoT that fires every [baseTicks] game ticks.
     *
     * The phase counts **ticks elapsed**, so one update adds 1; the interval is what `catalyze`
     * shortens. Adding the interval to the phase instead would make a 1.0x poison fire every tick.
     */
    class Cadence(private val baseTicks: Int) {
        private var phase = 0.0

        /** Advances one tick and reports whether the DoT should fire now. */
        fun advance(factor: Double): Boolean {
            val interval = baseTicks / factor.coerceAtLeast(1.0)
            phase += 1.0
            if (phase < interval) return false
            phase -= interval
            return true
        }

        fun reset() {
            phase = 0.0
        }
    }

    private class Entry(val ref: WeakReference<LivingEntity>) {
        var level = 0
        var ownerUUID: UUID? = null
        var expireAt = 0L
        var burnTicks = 0
        val factor: Double get() = 1.0 + level
    }

    private val entries = mutableMapOf<UUID, Entry>()

    fun apply(target: LivingEntity, level: Int, durationTicks: Long, owner: LivingEntity?) {
        if (level <= 0) return
        val existing = entries[target.uuid]
        val entry = if (existing != null && existing.ref.get() === target) existing
        else Entry(WeakReference(target)).also { entries[target.uuid] = it }

        val before = entry.factor
        entry.level = maxOf(entry.level, level)
        entry.ownerUUID = owner?.uuid ?: entry.ownerUUID
        entry.expireAt = target.world.time + durationTicks

        FSTLog.log("Catalyze") {
            "target=${target.name.string}, level=$level, factor=$before->${entry.factor}, " +
                "expireAt=${entry.expireAt}, outcome=dot-accelerated"
        }
    }

    fun factorOf(target: LivingEntity): Double = live(target)?.factor ?: 1.0

    /** Per-entity tick: retires lapsed entries and pays the extra burning damage. */
    fun tick(entity: LivingEntity) {
        // Keyed by UUID, so an entity that unloads before its entry lapses never gets collected; prune on a
        // tick we are already running instead of holding one row per mob forever.
        if (entries.size > 64 && entity.age % 200 == 0) prune(entity.world.time)

        val entry = live(entity) ?: return

        // `fire_master` already runs a heat-scaled burn ticker that multiplies this very factor in,
        // so paying the difference here as well would double-dip.
        if (FireBurn.hasEntry(entity)) {
            entry.burnTicks = 0
            return
        }

        if (entity.fireTicks <= 0 || entity.isFireImmune) {
            entry.burnTicks = 0
            return
        }

        entry.burnTicks++
        if (entry.burnTicks < FIRE_INTERVAL) return
        entry.burnTicks -= FIRE_INTERVAL

        val extra = (entry.factor - 1.0).toFloat()
        if (extra <= 0f) return

        val owner = entry.ownerUUID?.let { entity.world.getPlayerByUuid(it) }
        val landed = entity.damageElemental(owner, ElementType.FIRE.damageType, extra)
        FSTLog.log("Catalyze") {
            "target=${entity.name.string}, fireTicks=${entity.fireTicks}, extra=$extra, landed=$landed, " +
                "outcome=burn-catalyzed"
        }
    }

    private fun prune(now: Long) {
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next().value
            val alive = entry.ref.get()
            if (alive == null || alive.isRemoved || now > entry.expireAt) iterator.remove()
        }
    }

    private fun live(entity: LivingEntity): Entry? {
        val entry = entries[entity.uuid] ?: return null
        val expired = entity.world.time > entry.expireAt
        if (entry.ref.get() !== entity || entity.isRemoved || !entity.isAlive || expired) {
            entries.remove(entity.uuid)
            return null
        }
        return entry
    }
}
