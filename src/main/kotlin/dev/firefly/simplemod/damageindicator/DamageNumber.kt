package dev.firefly.simplemod.damageindicator

import dev.firefly.simplemod.core.config.DamageIndicatorConfig
import net.minecraft.entity.Entity

data class DamageNumber(
    val entity: Entity,
    var actualDamage: Float,
    var originalDamage: Float,
    val isHeal: Boolean = false,
    val isOverkill: Boolean = false,
    val isSelf: Boolean = false,
    val maxHealth: Float = 0f,
    val realMaxHealth: Float = 0f,
    val afterRealHealth: Float = 0f
) {
    var age: Int = 0

    val fixedX: Double = entity.posX + (Math.random() - 0.5) * 0.6
    val fixedZ: Double = entity.posZ + (Math.random() - 0.5) * 0.6
    private val startY: Double = entity.posY + entity.height

    private val MAX_RISE: Double =
        if (isSelf) 1.2
        else 1.05

    fun isExpired(): Boolean = age >= DamageIndicatorConfig.duration

    fun getCurrentY(): Double {
        val riseDuration = DamageIndicatorConfig.riseDuration.toDouble()
        val riseProgress = (age.toDouble() / riseDuration).coerceAtMost(1.0)
        val eased = 1.0 - (1.0 - riseProgress) * (1.0 - riseProgress)
        return (startY * DamageIndicatorConfig.yStartFactor) + eased * MAX_RISE
    }

    fun getAlpha(): Float {
        val duration = DamageIndicatorConfig.duration
        val fadeStart = duration * 0.6f
        return if (age < fadeStart) 1.0f else 1.0f - (age - fadeStart) / (duration - fadeStart)
    }

    fun getScale(): Float {
        val base = DamageIndicatorConfig.scale.toFloat()
        val selfScale = if (isSelf) 1.3f else 1.0f
        return base * selfScale * (1.0f - 0.15f * (age / DamageIndicatorConfig.duration.toFloat()))
    }

    fun getColor(): Int {
        return if (isHeal) 0x00FF00 else 0xFF3333
    }

    private fun formatFloat(value: Float): String {
        return if (value % 1.0f == 0.0f) {
            "${value.toInt()}"
        } else {
            String.format("%.2f", value)
        }
    }

    private fun getSign(): String {
        return if (DamageIndicatorConfig.symbol) {
            if (isHeal) "+" else "-"
        } else {
            ""
        }
    }

    fun getText(): String {
        val percentageMode = DamageIndicatorConfig.percentageMode
        val sign = getSign()

        return if (isHeal) {
            if (percentageMode) {
                val percent = (actualDamage / realMaxHealth * 100).coerceAtMost(100f)
                "$sign${formatFloat(percent)}%"
            } else {
                "$sign${formatFloat(actualDamage)}"
            }
        } else if (isOverkill) {
            if (percentageMode) {
                val actualPercent = (actualDamage / realMaxHealth * 100).coerceAtMost(100f)
                val originalPercent = (originalDamage / realMaxHealth * 100).coerceAtMost(100f)
                "$sign${formatFloat(originalPercent)}%(${formatFloat(actualPercent)}%)"
            } else {
                "$sign${formatFloat(originalDamage)}(${formatFloat(actualDamage)})"
            }
        } else {
            if (percentageMode) {
                val percent = (actualDamage / realMaxHealth * 100).coerceAtMost(100f)
                "$sign${formatFloat(percent)}%"
            } else {
                "$sign${formatFloat(actualDamage)}"
            }
        }
    }

    fun getSecondLineText(): String {
        val percentageMode = DamageIndicatorConfig.percentageMode
        val healthAfter = afterRealHealth.coerceAtLeast(0f)
        val maxHp = realMaxHealth.coerceAtLeast(1f)

        return if (percentageMode) {
            val percent = (healthAfter / maxHp * 100).coerceAtMost(100f)
            "${formatFloat(percent)}%"
        } else {
            "${formatFloat(healthAfter)}/${formatFloat(maxHp)}"
        }
    }

    fun getSecondLineColor(): Int {
        return if (isHeal) 0x66FF66 else 0xFF6666
    }
}