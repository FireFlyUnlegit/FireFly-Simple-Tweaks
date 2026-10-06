package dev.firefly.simpletweaks.damageindicator

import net.minecraft.entity.Entity
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Client-side list of live damage numbers. Ported 1:1 from 1.12.2
 * `damageindicator/DamageIndicatorManager.kt` (51 lines) — no API changes were needed.
 *
 * <p>[damageNumbers] is a [ConcurrentLinkedQueue] in the original and is kept that way: entries are
 * added from the packet receiver and removed/iterated from the render thread. (Fabric invokes
 * `ClientPlayNetworking` receivers on the client thread, so today both ends are the same thread — but
 * the original's choice costs nothing and removes the question.)
 *
 * <p>[update] ages every entry and drops the expired ones. It is called from the renderer, once per
 * frame, **not** once per tick — so `DamageIndicatorConfig.duration` is effectively counted in
 * *frames*, not ticks. That is 1.12.2 behaviour and is preserved deliberately; it is the kind of thing
 * that looks like a bug but is the actual shipped behaviour.
 */
object DamageIndicatorManager {

    private val damageNumbers = ConcurrentLinkedQueue<DamageNumber>()

    fun addDamage(
        entity: Entity,
        actualDamage: Float,
        originalDamage: Float,
        isHeal: Boolean = false,
        isOverkill: Boolean = false,
        isSelf: Boolean = false,
        maxHealth: Float = 0f,
        realMaxHealth: Float = 0f,
        afterRealHealth: Float = 0f,
    ) {
        damageNumbers.add(
            DamageNumber(
                entity,
                actualDamage,
                originalDamage,
                isHeal,
                isOverkill,
                isSelf,
                maxHealth,
                realMaxHealth,
                afterRealHealth,
            )
        )
    }

    fun getNumbers(): Collection<DamageNumber> = damageNumbers

    fun clear() {
        damageNumbers.clear()
    }

    fun update() {
        val iterator = damageNumbers.iterator()
        while (iterator.hasNext()) {
            val number = iterator.next()
            number.age++
            if (number.isExpired()) {
                iterator.remove()
            }
        }
    }
}
