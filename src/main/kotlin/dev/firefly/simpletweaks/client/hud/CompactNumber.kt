package dev.firefly.simpletweaks.client.hud

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * Short number text for anything that has to fit a fixed pixel width: `1.09k` / `12.3k` / `4.00M` / `1.20B`.
 *
 * Lives next to [BarNumber] rather than in `util/` because both callers are client drawing code (the HUD bars
 * and the floating damage numbers); neither the server nor any mixin formats numbers.
 */
object CompactNumber {

    /**
     * Suffix table, highest first: the first entry the value reaches decides the unit.
     *
     * The ladder is the SI one past `T`, because a modded health pool at 1e15 has to print as `1.00P`, not
     * `1000T`. Beyond `Y` there is nothing left, so those values keep a `Y` mantissa unless scientific
     * notation is switched on — see [of].
     */
    private val UNITS = listOf(
        1_000_000_000_000_000_000_000_000.0 to "Y",
        1_000_000_000_000_000_000_000.0 to "Z",
        1_000_000_000_000_000_000.0 to "E",
        1_000_000_000_000_000.0 to "P",
        1_000_000_000_000.0 to "T",
        1_000_000_000.0 to "B",
        1_000_000.0 to "M",
        1_000.0 to "k",
    )

    /**
     * Always compact when it can be: below 1 000 the value simply has no suffix.
     *
     * [scientific] swaps the whole ladder for `1.24e3` style text; it is a user setting rather than something
     * this function decides, because `E` (exa) and `e` (exponent) are easy to confuse on screen.
     */
    @JvmOverloads
    fun of(value: Double, places: Int, scientific: Boolean = false): String {
        if (!value.isFinite()) return "0"
        val abs = abs(value)
        if (scientific && abs >= 1_000.0) return scientificOf(value)
        // Past `Y` there is no unit left, so those values have to fall back to an exponent either way.
        if (abs >= 1_000_000_000_000_000_000_000_000_000.0) return scientificOf(value)
        var index = UNITS.indexOfFirst { abs >= it.first }
        if (index < 0) return BarNumber.of(value, places)

        // 999 999 rounds to "1000", which would print as `1000k`; climb a unit until the digits fit.
        while (index > 0 && abs / UNITS[index].first >= 999.995) index--

        val unit = UNITS[index]
        // A suffix already rounds, so two decimals carry the same information as four.
        return BarNumber.of(value / unit.first, 2) + unit.second
    }

    /** Compact only when [enabled] and the value is large enough to need it; plain text otherwise. */
    @JvmOverloads
    fun format(value: Double, places: Int, enabled: Boolean, scientific: Boolean = false): String {
        if (!enabled || !value.isFinite() || abs(value) < 1_000.0) return BarNumber.of(value, places)
        return of(value, places, scientific)
    }

    /** `1.24e3` / `3.4e38` — two decimals keep it inside the characters the suffixed forms use. */
    private fun scientificOf(value: Double): String {
        var exponent = floor(log10(abs(value))).toInt()
        var mantissa = value / 10.0.pow(exponent)
        // Rounding 9.996 up would print "10e3"; carry it into the exponent instead.
        if (abs(mantissa) >= 9.995) {
            mantissa /= 10.0
            exponent += 1
        }
        return BarNumber.of(mantissa, 2) + "e" + exponent
    }
}
