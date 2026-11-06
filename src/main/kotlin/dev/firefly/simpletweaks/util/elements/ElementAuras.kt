package dev.firefly.simpletweaks.util.elements

import dev.firefly.simpletweaks.compat.FSTLog
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import java.util.UUID
import java.util.WeakHashMap

/**
 * Elemental **aura** — the "how much element is on the target" number that the whole reaction theory
 * is built on.
 *
 * ## Why a gauge instead of a flag
 * `build=cleanup22` and earlier stored a mark as `(element, expireAt)`: an element was either present
 * or absent. That model cannot express any of the properties that make a reaction system interesting —
 * a strong application surviving a weak trigger, a trigger being consumed only partially, two hits of
 * the same element stacking, or a reaction firing on the *leftover* of the previous one.
 *
 * So an aura carries **units** (`元素量`), decays linearly, and every reaction is resolved as
 * *consume units → maybe react → keep the remainder*.
 *
 * | class | units | lifetime | typical source in this mod |
 * |---|---|---|---|
 * | 弱 (weak) | `1.0` | 9.5 s (190 t) | most elemental hits, chain arcs |
 * | 强 (strong) | `2.0` | 12 s (240 t) | conversion riders, starfall, holy light |
 * | 超强 (ultra) | `4.0` | 17 s (340 t) | `heavenly_punishment` |
 *
 * Decay is **linear over the lifetime above**, i.e. `units / lifetime` per tick. Aura lifetime is a
 * property of the *amount*, matching the reference behaviour rather than a fixed duration.
 *
 * ## State discipline
 * Same rules as the old mark table, and they still hold: [WeakHashMap] keyed by the entity object, no
 * NBT, no syncing, no tick handler. Everything is read lazily by "the next hit", so a despawned entity
 * simply takes its aura with it.
 */
object ElementAuras {

    /** 弱元素: one "normal" application. */
    const val WEAK = 1.0f

    /** 强元素: a conversion rider or a "skill-grade" hit. */
    const val STRONG = 2.0f

    /** 超强元素: only the loudest effects (heavenly punishment). */
    const val ULTRA = 4.0f

    /** Lifetime in ticks for each class (9.5 s / 12 s / 17 s). */
    const val WEAK_TICKS = 190
    const val STRONG_TICKS = 240
    const val ULTRA_TICKS = 340

    /**
     * One live aura.
     *
     * [decayPerTick] is derived from the **initial** amount, so a partially consumed aura still dies at
     * the time its class implies: consuming does not slow the clock down.
     */
    private class Aura(
        val element: ElementType,
        val unitsAtAttach: Float,
        val decayPerTick: Float,
        val attachedAt: Long,
    ) {
        fun unitsAt(now: Long): Float {
            val elapsed = (now - attachedAt).coerceAtLeast(0L)
            return unitsAtAttach - decayPerTick * elapsed
        }
    }

    private val auras = WeakHashMap<LivingEntity, Aura>()

    /** Lifetime of an application of [units] (the three documented classes, clamped for odd values). */
    private fun lifetimeTicks(units: Float): Int = when {
        units <= WEAK + 0.001f -> WEAK_TICKS
        units <= STRONG + 0.001f -> STRONG_TICKS
        else -> ULTRA_TICKS
    }

    /** Attaches [units] of [element], **replacing** whatever was there (same element stacks to max). */
    fun attach(entity: LivingEntity, element: ElementType, units: Float = WEAK) {
        val now = entity.world.time
        val existing = auras[entity]
        val merged =
            if (existing != null && existing.element == element) {
                // Same element on the same target refreshes to the stronger of the two applications
                // rather than adding up: `1 + 1 + 1` would out-class an ultra application for free.
                maxOf(existing.unitsAt(now), units)
            } else {
                units
            }
        val lifetime = lifetimeTicks(merged)
        auras[entity] = Aura(element, merged, merged / lifetime, now)
    }

    /** The live aura as `(element, units)`, or `null` when there is none left. */
    fun peek(entity: LivingEntity): Pair<ElementType, Float>? {
        val aura = auras[entity] ?: return null
        val units = aura.unitsAt(entity.world.time)
        if (units <= 0.001f) {
            auras.remove(entity)
            return null
        }
        return aura.element to units
    }

    /**
     * Consumes [units] from the aura.
     *
     * @return the units that were **actually** consumed (never more than remain)
     */
    fun consume(entity: LivingEntity, units: Float): Float {
        val aura = auras[entity] ?: return 0f
        val now = entity.world.time
        val remaining = aura.unitsAt(now)
        if (remaining <= 0.001f) {
            auras.remove(entity)
            return 0f
        }
        val taken = minOf(remaining, units)
        val left = remaining - taken
        if (left <= 0.001f) {
            auras.remove(entity)
        } else {
            // Re-anchor so the linear decay continues from the *remaining* amount at the same rate.
            val rate = aura.decayPerTick
            val elapsed = (now - aura.attachedAt).coerceAtLeast(0L)
            auras[entity] = Aura(aura.element, left + rate * elapsed, rate, aura.attachedAt)
        }
        return taken
    }

    /** Drops the aura entirely (used when a reaction consumes the last of it). */
    fun clear(entity: LivingEntity) {
        auras.remove(entity)
    }

    /** Element of the live aura, or `null`. Kept for the debug command and the log lines. */
    fun elementOf(entity: LivingEntity): ElementType? = peek(entity)?.first

    /** Log helper: `none` or `fire:1.42`. */
    fun describe(entity: LivingEntity): String {
        val live = peek(entity) ?: return "none"
        return "${live.first.id}:${"%.2f".format(live.second)}"
    }
}

/**
 * Elemental attachment **ICD** — the "double counter" that stops a fast attacker from dumping two
 * auras per second.
 *
 * Two counters, exactly as in the reference:
 * - **time**: a hit at least [WINDOW_TICKS] (2.5 s) after the last *applied* hit always attaches, and
 *   resets the hit counter;
 * - **count**: otherwise only every [HITS_PER_APPLY]-th hit attaches ("三击定律").
 *
 * The time counter has priority: when it fires, the count is cleared, so a normal swing cadence always
 * sees the first hit of a new window attach.
 *
 * Keyed per (attacker, target). A `null` attacker (world / environment damage) bypasses the ICD — there
 * is no "attack class" to throttle.
 */
object AttachmentIcd {

    /** 2.5 s. */
    const val WINDOW_TICKS = 50L

    /** 1st, 4th, 7th … hit attaches. */
    const val HITS_PER_APPLY = 3

    private class State {
        var lastAppliedAt: Long = Long.MIN_VALUE / 2
        var hitsInWindow: Int = 0
    }

    private val states = WeakHashMap<LivingEntity, MutableMap<UUID, State>>()

    /**
     * @return true when this hit is allowed to attach an aura
     */
    fun allows(target: LivingEntity, attacker: PlayerEntity?): Boolean {
        if (attacker == null) return true
        val now = target.world.time
        val perTarget = states.getOrPut(target) { HashMap() }
        val state = perTarget.getOrPut(attacker.uuid) { State() }

        if (now - state.lastAppliedAt >= WINDOW_TICKS) {
            state.hitsInWindow = 0
        }
        state.hitsInWindow++

        if (state.hitsInWindow % HITS_PER_APPLY == 1) {
            state.lastAppliedAt = now
            FSTLog.log("Element") {
                "outcome=attachment-allowed, target=${target.type.name.string}, " +
                    "hitsInWindow=${state.hitsInWindow}, sinceLast=${now - state.lastAppliedAt}"
            }
            return true
        }
        FSTLog.log("Element") {
            "outcome=attachment-throttled, target=${target.type.name.string}, " +
                "hitsInWindow=${state.hitsInWindow}, windowEndsIn=" +
                "${WINDOW_TICKS - (now - state.lastAppliedAt)}"
        }
        return false
    }

    /** Test/debug hook: forget every counter (used by `/fst` diagnostics). */
    fun reset() {
        states.clear()
    }
}
