package dev.firefly.simpletweaks.util

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.util.damagesource.damageElemental
import dev.firefly.simpletweaks.util.elements.ElementType
import net.minecraft.entity.LivingEntity
import java.lang.ref.WeakReference
import java.util.UUID

/**
 * Burn engine behind `fire_master`: stacked burn time (`fireTicks`) is the heat meter, and the
 * hotter it is the faster and harder the extra burn ticks land.
 *
 * Vanilla still pays its own 1 damage every 20 ticks; [tick] only adds the extra ticks on top.
 */
object FireBurn {

    /** 10 s, the ceiling `fire_master` may stack a target to. */
    const val MAX_FIRE_TICKS = 200

    private const val BASE_INTERVAL = 20.0
    private const val MAX_SPEED = 3.0
    private const val TICKS_PER_LEVEL = 10

    private class Entry(val ref: WeakReference<LivingEntity>) {
        var level = 0
        var ownerUUID: UUID? = null
        var phase = 0.0
        var expireAt = 0L
    }

    private val entries = mutableMapOf<UUID, Entry>()

    /** Ticks between two extra burn ticks: 20 at no heat, 5 at [MAX_FIRE_TICKS]. */
    @JvmStatic
    fun intervalFor(fireTicks: Int): Double = BASE_INTERVAL / (1.0 + MAX_SPEED * heatOf(fireTicks))

    /** Extra damage of one burn tick: scales with both level and heat. */
    @JvmStatic
    fun damageFor(level: Int, fireTicks: Int): Double {
        if (level <= 0) return 0.0
        val heat = heatOf(fireTicks)
        return level * (0.05 + 0.10 * heat)
    }

    /** New `fireTicks` after one hit; never shortens a longer burn from lava. */
    @JvmStatic
    fun ignitedTicks(current: Int, level: Int): Int {
        if (level <= 0) return current
        val cap = maxOf(MAX_FIRE_TICKS, current)
        return minOf(current + level * TICKS_PER_LEVEL, cap)
    }

    fun ignite(target: LivingEntity, level: Int, owner: LivingEntity?) {
        if (level <= 0 || target.isFireImmune) return

        val before = target.fireTicks
        target.fireTicks = ignitedTicks(before, level)

        val existing = entries[target.uuid]
        val entry = if (existing != null && existing.ref.get() === target) existing
        else Entry(WeakReference(target)).also { entries[target.uuid] = it }
        entry.level = maxOf(entry.level, level)
        entry.ownerUUID = owner?.uuid ?: entry.ownerUUID
        // The accelerated burn belongs to your hit, not to whatever keeps the target burning afterwards
        // (lava refills `fireTicks` indefinitely), so the entry expires even if the fire never does.
        entry.expireAt = target.world.time + MAX_FIRE_TICKS

        FSTLog.log("FireMaster") {
            "target=${target.name.string}, level=$level, fireTicks=$before->${target.fireTicks} (cap=$MAX_FIRE_TICKS), " +
                "interval=${intervalFor(target.fireTicks)}, tickDamage=${damageFor(entry.level, target.fireTicks)}, outcome=ignited"
        }
    }

    fun hasEntry(target: LivingEntity): Boolean = live(target) != null

    fun tick(entity: LivingEntity) {
        val entry = live(entity) ?: return
        if (entity.fireTicks <= 0 || entity.isFireImmune) {
            entries.remove(entity.uuid)
            return
        }

        entry.phase += (1.0 + MAX_SPEED * heatOf(entity.fireTicks)) * DotAcceleration.factorOf(entity)
        if (entry.phase < BASE_INTERVAL) return
        entry.phase -= BASE_INTERVAL

        val damage = damageFor(entry.level, entity.fireTicks).toFloat()
        if (damage <= 0f) return

        val owner = entry.ownerUUID?.let { entity.world.getPlayerByUuid(it) }
        val landed = entity.damageElemental(owner, ElementType.FIRE.damageType, damage)
        FSTLog.log("FireMaster") {
            "target=${entity.name.string}, fireTicks=${entity.fireTicks}, tickDamage=$damage, landed=$landed, " +
                "catalyze=${DotAcceleration.factorOf(entity)}, outcome=burn-tick"
        }
    }

    private fun heatOf(fireTicks: Int): Double =
        (fireTicks.toDouble() / MAX_FIRE_TICKS).coerceIn(0.0, 1.0)

    private fun live(entity: LivingEntity): Entry? {
        val entry = entries[entity.uuid] ?: return null
        if (entry.ref.get() !== entity || entity.isRemoved || !entity.isAlive ||
            entity.world.time > entry.expireAt
        ) {
            entries.remove(entity.uuid)
            return null
        }
        return entry
    }
}
