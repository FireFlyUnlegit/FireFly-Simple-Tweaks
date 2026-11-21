package dev.firefly.simpletweaks.client.hud

import kotlin.math.abs

/**
 * Short number text for anything that has to fit a fixed pixel width: `1.09k` / `12.3k` / `4.00M` / `1.20B`.
 *
 * Lives next to [BarNumber] rather than in `util/` because both callers are client drawing code (the HUD bars
 * and the floating damage numbers); neither the server nor any mixin formats numbers.
 */
object CompactNumber {

    /** Suffix threshold, highest first: the first entry the value reaches decides the unit. */
    private val UNITS = listOf(
        1_000_000_000_000.0 to "T",
        1_000_000_000.0 to "B",
        1_000_000.0 to "M",
        1_000.0 to "k",
    )

    /** Always compact when it can be: below 1 000 the value simply has no suffix. */
    fun of(value: Double, places: Int): String {
        if (!value.isFinite()) return "0"
        val abs = abs(value)
        val unit = UNITS.firstOrNull { abs >= it.first }
            ?: return BarNumber.of(value, places)
        val scaled = value / unit.first
        // A suffix already rounds, so two decimals carry the same information as four.
        return BarNumber.of(scaled, 2) + unit.second
    }

    /** Compact only when [enabled] and the value is large enough to need it; plain text otherwise. */
    fun format(value: Double, places: Int, enabled: Boolean): String {
        if (!enabled || !value.isFinite() || abs(value) < 1_000.0) return BarNumber.of(value, places)
        return of(value, places)
    }
}
