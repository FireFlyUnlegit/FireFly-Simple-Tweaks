package dev.firefly.simpletweaks.client.hud

import java.util.Locale

/** Bar-number text: at most [places] decimals, trailing zeros trimmed. Pure, so the gametest can call it. */
object BarNumber {

    fun of(value: Double, places: Int): String {
        if (!value.isFinite()) return "0"
        val digits = places.coerceIn(0, 4)
        var text = String.format(Locale.ROOT, "%.${digits}f", value)
        if (digits > 0) {
            text = text.trimEnd('0').trimEnd('.')
            if (text.isEmpty() || text == "-") text = "0"
        }
        return if (text == "-0") "0" else text
    }
}
