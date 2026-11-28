package dev.firefly.simpletweaks.logic

/**
 * The element damage arithmetic, with no Minecraft types anywhere in it.
 *
 * Extracted from `ElementCombat` so the curves can be unit tested (`:logic:test`) instead of only
 * being observable on a live server. Values and behaviour are unchanged — every function here is a
 * verbatim move of the expression it replaces.
 *
 * `ElementCombat` keeps the same public names as thin delegates, so call sites did not move.
 */
object ElementMath {

    // ---------------------------------------------------------------- conversion tuning

    const val BASE_FRACTION = 0.015f
    const val PER_LEVEL_FRACTION = 0.015f
    const val MAX_FRACTION = 0.075f
    const val ELEMENT_SCALE = 1.0f
    const val BONUS_SCALE = 0.5f

    // ---------------------------------------------------------------- protection tuning

    const val PROTECTION_PER_LEVEL = 0.04f
    const val PROTECTION_CAP = 0.80f
    const val SUPREMACY_PER_LEVEL = 0.045f
    const val SUPREMACY_CAP = 0.90f

    // ---------------------------------------------------------------- mastery tuning

    const val MASTERY_PER_LEVEL = 0.10f
    const val MASTERY_CAP = 2.0f

    // ---------------------------------------------------------------- cross-element tuning

    const val WARD_PER_LEVEL = 0.03f
    const val WARD_CAP = 0.09f
    const val RESONANCE_PER_LEVEL = 0.10f
    const val RESONANCE_CAP = 0.30f
    const val PENETRATION_PER_LEVEL = 0.08f
    const val PENETRATION_CAP = 0.24f

    /** The converted share of a hit at [level]: `BASE + PER_LEVEL × (level - 1)`, capped. */
    fun conversionFraction(level: Int): Float {
        if (level <= 0) return 0f
        return (BASE_FRACTION + PER_LEVEL_FRACTION * (level - 1)).coerceAtMost(MAX_FRACTION)
    }

    /**
     * The re-dealt elemental half of a converted hit:
     * `(ELEMENT_SCALE + bonusScale) × fraction × mastery`.
     *
     * Shared by the player path (fixed [BONUS_SCALE]) and the mob path (its own bonus), so the two can
     * not drift into two different "conversion" shapes.
     */
    fun elementalShare(fraction: Float, bonusScale: Float = BONUS_SCALE, mastery: Float = 1.0f): Float {
        if (fraction <= 0f) return 0f
        return (ELEMENT_SCALE + bonusScale) * fraction * mastery
    }

    /** Net damage multiplier of a conversion with an explicit [fraction] and bonus. */
    fun netMultiplierFor(fraction: Float, bonusScale: Float = BONUS_SCALE, mastery: Float = 1.0f): Float {
        if (fraction <= 0f) return 1.0f
        return (1.0f - fraction) + elementalShare(fraction, bonusScale, mastery)
    }

    /**
     * Net damage of a conversion at [level] relative to the unconverted hit:
     * `(1 − f) + (ELEMENT_SCALE + BONUS_SCALE) × f × mastery`.
     *
     * With the shipped constants this is `1 + 0.5 × f × mastery`, i.e. never below `1`: a conversion
     * may not make a hit smaller. `ElementMathTest` asserts that for a whole level range.
     */
    fun netMultiplier(level: Int, mastery: Float = 1.0f): Float =
        netMultiplierFor(conversionFraction(level), BONUS_SCALE, mastery)

    /** Mastery multiplier: `1 + MASTERY_PER_LEVEL × level`, capped; `1.0` when absent. */
    fun masteryMultiplier(level: Int): Float {
        if (level <= 0) return 1.0f
        return (1.0f + MASTERY_PER_LEVEL * level).coerceAtMost(1.0f + MASTERY_CAP)
    }

    /** One element's protection reduction at [level], capped. */
    fun protectionReduction(level: Int): Float {
        if (level <= 0) return 0f
        return (PROTECTION_PER_LEVEL * level).coerceAtMost(PROTECTION_CAP)
    }

    /** `elemental_supremacy` reduction at the summed armour level, capped. */
    fun supremacyReduction(totalLevel: Int): Float {
        if (totalLevel <= 0) return 0f
        return (SUPREMACY_PER_LEVEL * totalLevel).coerceAtMost(SUPREMACY_CAP)
    }

    /**
     * Shortening factor applied to a reaction's internal cooldown at [totalLevel] of
     * `elemental_supremacy`: `1 - totalLevel / 20`, clamped to `0..1`.
     */
    fun supremacyHaste(totalLevel: Int): Float = (1.0f - totalLevel / 20.0f).coerceIn(0f, 1f)

    /** `elemental_ward` reduction at [level], capped. */
    fun wardReduction(level: Int): Float {
        if (level <= 0) return 0f
        return (WARD_PER_LEVEL * level).coerceAtMost(WARD_CAP)
    }

    /** Reaction damage multiplier from `elemental_resonance` at [level], capped. */
    fun resonanceMultiplier(level: Int): Float {
        return 1.0f + if (level <= 0) 0f else (RESONANCE_PER_LEVEL * level).coerceAtMost(RESONANCE_CAP)
    }

    /** Share of the target's elemental mitigation that `elemental_penetration` ignores at [level]. */
    fun penetration(level: Int): Float {
        if (level <= 0) return 0f
        return (PENETRATION_PER_LEVEL * level).coerceAtMost(PENETRATION_CAP)
    }

    /**
     * Combines the independent mitigation sources and the attacker's penetration into the share of
     * the hit that survives.
     *
     * Mitigations multiply (so no single source can reach 100 %), penetration multiplies the combined
     * mitigation (so it cannot turn 80 % protection into a negative), and the result is clamped to
     * `0..1` purely as a numeric guard against NaN or a bad penetration value.
     */
    fun mitigatedShare(
        enchantReduction: Float,
        mobReduction: Float,
        wardReduction: Float,
        supremacyReduction: Float,
        penetration: Float,
    ): Float {
        val mitigated = 1.0f - (1.0f - enchantReduction) * (1.0f - mobReduction) *
            (1.0f - wardReduction) * (1.0f - supremacyReduction)
        return (mitigated * (1.0f - penetration)).coerceIn(0f, 1f)
    }
}
