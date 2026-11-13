package dev.firefly.simpletweaks.util

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.WorldSide
import dev.firefly.simpletweaks.compat.event.LivingEvent
import dev.firefly.simpletweaks.compat.event.LivingHealEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.core.Listenable
import net.minecraft.entity.LivingEntity
import java.util.WeakHashMap

/** "Cannot be healed until tick N". Used by `echo_shield` and `elemental_supremacy`; deadlines are not persisted. */
object HealLock : Listenable {

    private val UNTIL = WeakHashMap<LivingEntity, Long>()

    /** A longer existing lock is never shortened. */
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
