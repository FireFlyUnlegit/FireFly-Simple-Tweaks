package dev.firefly.simpletweaks.util

import java.util.concurrent.ThreadLocalRandom

/**
 * Picks the level an advanced form comes out at.
 *
 * Low levels must stay common: the curve is a decreasing weight over `1..max`, so a max-5 form is
 * roughly 33 % I, 27 % II, 20 % III, 13 % IV, 7 % V. Ties are broken by a single roll.
 */
object AdvancedLevels {

    @JvmStatic
    fun pick(maxLevel: Int): Int {
        val max = maxLevel.coerceAtLeast(1)
        if (max == 1) return 1
        var total = 0
        for (level in 1..max) total += max - level + 1
        var roll = ThreadLocalRandom.current().nextInt(total)
        for (level in 1..max) {
            roll -= max - level + 1
            if (roll < 0) return level
        }
        return 1
    }
}
