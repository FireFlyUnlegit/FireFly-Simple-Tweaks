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
     * `1000T`. `E` (exa) is the one ambiguous entry — mitigated by the mantissa always carrying two decimals,
     * while the scientific fallback below uses one and a lowercase `e`.
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

    /** Past this the smallest suffix would print four digits of mantissa, so switch to `1.2e27` instead. */
    private const val SCIENTIFIC_FLOOR = 1_000_000_000_000_000_000_000_000_000.0

    /** Always compact when it can be: below 1 000 the value simply has no suffix. */
    fun of(value: Double, places: Int): String {
        if (!value.isFinite()) return "0"
        val abs = abs(value)
        if (abs >= SCIENTIFIC_FLOOR) return scientific(value)
        var index = UNITS.indexOfFirst { abs >= it.first }
        if (index < 0) return BarNumber.of(value, places)

        // 999 999 rounds to "1000", which would print as `1000k`; climb a unit until the digits fit.
        while (index > 0 && abs / UNITS[index].first >= 999.995) index--

        val unit = UNITS[index]
        // A suffix already rounds, so two decimals carry the same information as four.
        return BarNumber.of(value / unit.first, 2) + unit.second
    }

    /** Compact only when [enabled] and the value is large enough to need it; plain text otherwise. */
    fun format(value: Double, places: Int, enabled: Boolean): String {
        if (!enabled || !value.isFinite() || abs(value) < 1_000.0) return BarNumber.of(value, places)
        return of(value, places)
    }

    /** `3.4e38` — one decimal keeps it inside the same 6–7 characters the suffixed forms use. */
    private fun scientific(value: Double): String {
        var exponent = floor(log10(abs(value))).toInt()
        var mantissa = value / 10.0.pow(exponent)
        // Rounding 9.96 up would print "10e38"; carry it into the exponent instead.
        if (abs(mantissa) >= 9.95) {
            mantissa /= 10.0
            exponent += 1
        }
        return BarNumber.of(mantissa, 1) + "e" + exponent
    }
}
