package dev.firefly.simpletweaks.damageindicator

import dev.firefly.simpletweaks.core.config.DamageIndicatorConfig
import net.minecraft.entity.Entity

/**
 * One floating damage number. Ported 1:1 from 1.12.2 `damageindicator/DamageNumber.kt` (113 lines).
 *
 * <h2>1.12.2 -&gt; 1.21.1 mapping</h2>
 * | 1.12.2 | 1.21.1 |
 * |---|---|
 * | `entity.posX` / `posY` / `posZ` | `entity.x` / `entity.y` / `entity.z` |
 * | `entity.height` | `entity.height` (`Entity#getHeight`) |
 *
 * Nothing else changed: every formula below (rise easing, fade window, scale decay, colour constants,
 * sign handling, overkill rendering, the `%` clamping and the second line) is the original arithmetic.
 * In particular the deliberate quirks are preserved rather than "fixed":
 * <ul>
 *   <li>[getAlpha] starts fading at 60% of `duration` and can in principle divide by zero if
 *       `duration == fadeStart`, i.e. `duration == 0` — the config's lower bound is 10, so it cannot.</li>
 *   <li>`isOverkill` renders `original(actual)`, which reads oddly but is what 1.12.2 did.</li>
 *   <li>`formatFloat` drops the decimals for whole numbers, so `5.0` renders as `5`, not `5.00`.</li>
 * </ul>
 *
 * <p>[fixedX]/[fixedZ], [entityFeetY] and [entityHeight] are computed **once, at construction**, from
 * the entity's position at the moment the packet arrived — that is why a number stays where it was
 * spawned instead of following the mob. This is load-bearing: the randomness is sampled once, not per
 * frame.
 */
data class DamageNumber(
    val entity: Entity,
    var actualDamage: Float,
    var originalDamage: Float,
    val isHeal: Boolean = false,
    val isOverkill: Boolean = false,
    val isSelf: Boolean = false,
    val maxHealth: Float = 0f,
    val realMaxHealth: Float = 0f,
    val afterRealHealth: Float = 0f,
) {
    var age: Int = 0

    val fixedX: Double = entity.x + (Math.random() - 0.5) * 0.6
    val fixedZ: Double = entity.z + (Math.random() - 0.5) * 0.6
    /**
     * The entity's feet Y and its height, sampled once at construction.
     *
     * These are cached **separately** rather than as a single `startY` sum because
     * [DamageIndicatorConfig.yStartFactor] multiplies the height term only, not the absolute world
     * height. See [getCurrentY].
     */
    private val entityFeetY: Double = entity.y
    private val entityHeight: Double = entity.height.toDouble()

    private val MAX_RISE: Double = if (isSelf) 1.2 else 1.05

    fun isExpired(): Boolean = age >= DamageIndicatorConfig.duration

    /**
     * The number's current world Y: the spawn anchor plus the eased rise.
     *
     * The anchor is `feetY + height * yStartFactor`, i.e. [DamageIndicatorConfig.yStartFactor] is a
     * multiplier on the **entity's height** and `1.0` puts the number exactly on top of the hitbox.
     *
     * 1.12.2 instead computed `(posY + height) * factor`, multiplying the *absolute world Y*: at
     * y=70 that same `factor = 2.0` spawned the number near y=143, further from the camera than
     * `maxDistance`, so [DamageIndicatorRenderer] skipped it and the indicator silently vanished —
     * with no vanilla fallback, because `cancelVanillaDamageIndicator` defaults to true. Multiplying
     * the height term keeps every value in `0.5..2.0` inside render range while being **bit-identical
     * to 1.12.2 at the default `1.0`**.
     *
     * [entityFeetY] and [entityHeight] are cached, not re-read from [entity], so the number still
     * does not follow the mob.
     */
    fun getCurrentY(): Double {
        val riseDuration = DamageIndicatorConfig.riseDuration.toDouble()
        val riseProgress = (age.toDouble() / riseDuration).coerceAtMost(1.0)
        val eased = 1.0 - (1.0 - riseProgress) * (1.0 - riseProgress)
        val anchorY = entityFeetY + entityHeight * DamageIndicatorConfig.yStartFactor
        return anchorY + eased * MAX_RISE
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
