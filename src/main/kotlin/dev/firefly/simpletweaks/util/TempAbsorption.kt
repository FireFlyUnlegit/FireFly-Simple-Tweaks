package dev.firefly.simpletweaks.util

import dev.firefly.simpletweaks.compat.FSTLog
import net.minecraft.entity.LivingEntity
import java.util.UUID

/**
 * A hit-counted shield: it blocks a number of hits *and* carries a value pool.
 *
 * Unlike vanilla absorption the shield does not merely add health: every hit it takes spends one
 * charge and subtracts exactly the damage it swallowed. When either the pool or the charges run
 * out the shield disappears, so any damage it could not cover lands on the wearer.
 */
object TempAbsorption {

    /** Hard ceiling: a shield never covers more hits than this, whatever granted it. */
    const val MAX_CHARGES = 3

    private class Shield(@JvmField var value: Float, @JvmField var charges: Int)

    private val shields = java.util.Collections.synchronizedMap(HashMap<UUID, Shield>())

    @JvmStatic
    fun grant(entity: LivingEntity, value: Float, charges: Int) {
        if (!value.isFinite() || value <= 0f) return
        val wanted = charges.coerceIn(1, MAX_CHARGES)
        val existing = shields[entity.uuid]
        if (existing == null) {
            shields[entity.uuid] = Shield(value, wanted)
        } else {
            existing.value += value
            existing.charges = wanted
        }
        FSTLog.log("TempAbsorption") {
            "entity=${entity.name.string}, value=$value, charges=$wanted, outcome=granted"
        }
    }

    /** Swallows as much of [damage] as the shield can, returns the absorbed amount. */
    @JvmStatic
    fun absorb(entity: LivingEntity, damage: Float): Float {
        if (!damage.isFinite() || damage <= 0f) return 0f
        val shield = shields[entity.uuid] ?: return 0f
        if (shield.charges <= 0 || shield.value <= 0f) {
            shields.remove(entity.uuid)
            return 0f
        }
        val absorbed = minOf(shield.value, damage)
        shield.value -= absorbed
        shield.charges--
        if (shield.value <= 0f || shield.charges <= 0) shields.remove(entity.uuid)
        FSTLog.log("TempAbsorption") {
            "entity=${entity.name.string}, damage=$damage, absorbed=$absorbed, " +
                "left=${shield.value}, charges=${shield.charges}, outcome=absorbed"
        }
        return absorbed
    }

    @JvmStatic
    fun peek(entity: LivingEntity): Pair<Float, Int>? =
        shields[entity.uuid]?.let { it.value to it.charges }

    @JvmStatic
    fun clear(entity: LivingEntity) {
        shields.remove(entity.uuid)
    }
}
