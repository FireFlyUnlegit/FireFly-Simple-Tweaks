package dev.firefly.simplemod.damageindicator

import net.minecraft.entity.Entity
import java.util.concurrent.ConcurrentLinkedQueue

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
        afterRealHealth: Float = 0f
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
                afterRealHealth
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