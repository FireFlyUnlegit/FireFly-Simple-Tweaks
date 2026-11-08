package dev.firefly.simpletweaks.util

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.WorldSide
import dev.firefly.simpletweaks.compat.event.LivingEvent
import dev.firefly.simpletweaks.compat.event.LivingHealEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.core.Listenable
import net.minecraft.entity.LivingEntity
import java.util.WeakHashMap

/**
 * 禁疗 — "this entity cannot be healed until tick N", the one implementation in the mod.
 *
 * ## Who uses it
 * | source | duration |
 * |---|---|
 * | `echo_shield` (being blocked) | `level` ticks — the 1.12.2 value, kept verbatim |
 * | `elemental_supremacy` (countering an elemental hit) | `total level × 10` ticks (0.5 s per level, 10 s at a 20-level set) |
 *
 * ## Why a shared object and not a map per handler
 * `echo_shield` carried its own `WeakHashMap` + `LivingHealEvent` + `LivingUpdateEvent` trio for this.
 * Adding a second copy for 万法 would mean two cancel paths that can silently disagree (one cancels the
 * event, the other zeroes the amount, …) and two expiry sweeps. One owner, one behaviour.
 *
 * ## Why `WeakHashMap` and not entity NBT
 * The value is a `world.time` deadline measured in seconds of combat. Persisting it would restore a
 * stale deadline after a reload — 1.12.2 stored it in NBT because its only alternative was awkward, not
 * because the data is worth keeping. The key is the entity itself, so a removed entity takes its entry
 * with it (`AGENTS.md` §4.11's leak family).
 *
 * ## Why it is registered by hand
 * It is not an enchantment: `EnchantmentManager.initHandlers` registers it next to `MobLevelProgression`
 * so it stays out of the "Registered N enchantment handler(s)" count.
 */
object HealLock : Listenable {

    /** Entity → first tick at which it may be healed again. */
    private val UNTIL = WeakHashMap<LivingEntity, Long>()

    /** Forbid healing [entity] for [ticks]; an existing, longer lock is never shortened. */
    @JvmStatic
    fun lock(entity: LivingEntity, ticks: Long) {
        if (ticks <= 0L) return
        val until = entity.world.time + ticks
        val previous = UNTIL[entity] ?: 0L
        if (until > previous) {
            UNTIL[entity] = until
            FSTLog.log("HealLock") {
                "entity=${entity.type.name.string}, ticks=$ticks, until=$until, " +
                    "previous=$previous, outcome=locked"
            }
        }
    }

    /** Remaining locked ticks for [entity], `0` when it can heal. Diagnostics and tests. */
    @JvmStatic
    fun remaining(entity: LivingEntity): Long {
        val until = UNTIL[entity] ?: return 0L
        return (until - entity.world.time).coerceAtLeast(0L)
    }

    @SubscribeEvent
    fun onHeal(e: LivingHealEvent) {
        val entity = e.entityLiving
        if (WorldSide.isClient(entity.world)) return
        val until = UNTIL[entity] ?: return
        if (entity.world.time < until) {
            e.isCanceled = true
            e.amount = 0f
            FSTLog.log("HealLock") {
                "entity=${entity.type.name.string}, until=$until, now=${entity.world.time}, " +
                    "amount=${e.amount}, outcome=heal-blocked"
            }
        }
    }

    @SubscribeEvent
    fun onLivingUpdate(e: LivingEvent.LivingUpdateEvent) {
        val entity = e.entityLiving
        if (WorldSide.isClient(entity.world)) return
        val until = UNTIL[entity] ?: return
        if (entity.world.time > until) UNTIL.remove(entity)
    }
}
