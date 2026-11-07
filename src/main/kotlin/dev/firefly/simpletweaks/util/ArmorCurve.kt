package dev.firefly.simpletweaks.util

import net.minecraft.entity.LivingEntity

/**
 * The **armour curve** — what a point of armour is worth above vanilla's wall.
 *
 * ## The problem (vanilla 1.21.1, `DamageUtil#getDamageLeft`, read with `javap -c`)
 *
 * ```text
 *   f = 2.0 + toughness / 4.0
 *   g = clamp(armor - damage / f, armor * 0.2, 20.0)   // ← the wall: 20.0
 *   h = g / 25.0                                        // percentage, max 0.8
 *   … armor_effectiveness hook …
 *   return damage * (1 - h)
 * ```
 *
 * Armour 20 is `80 %`, and **every point above 20 is discarded by that `clamp`**. This mod's own content
 * (`extra_armor`, `prismatic_blessing`, `adrenaline`, level-scaled gear) can push a player past 20, so
 * the author asked for something where **no point is ever wasted**.
 *
 * ## The reformed model (`build=cleanup26`) — percentage, then flat
 *
 * Instead of stretching one percentage, the overflow armour changes **kind**:
 *
 * | armour | what it does |
 * |---|---|
 * | `0 … 20` | vanilla's percentage, untouched (max 80 %) |
 * | `> 20` | the percentage stays at its 80 % wall; each extra point adds a **flat** reduction of [FLAT_PER_POINT] damage per hit |
 *
 * Flat is the right second kind because it does what a percentage cannot: it makes heavy armour shine
 * against **many small hits** (the actual failure mode of the 80 % wall — a 6-damage zombie hit at 80 %
 * still removes 1.2, and 20 more armour points changed nothing).
 *
 * ### Why the flat part is capped by a fraction of what is left
 *
 * Uncapped, armour 60 would remove 20 flat per hit: a 6-damage hit becomes 0, and the player is simply
 * immune to anything weak. The flat part is therefore bounded by [MAX_FLAT_FRACTION] of the damage that
 * survived the percentage, which keeps the model honest: **better armour always helps, and no amount of
 * it makes a hit free**.
 *
 * Composite taken-damage at a 20-damage hit:
 *
 * | armour | percentage part | remaining | flat part | taken | total reduction |
 * |---|---|---|---|---|---|
 * | 20 | 80 % | 4.0 | 0 | 4.0 | 80 % |
 * | 26 | 80 % | 4.0 | 3.0 (75 % of 4) | 1.0 | 95 % |
 * | 40 | 80 % | 4.0 | 3.0 (capped) | 1.0 | 95 % |
 *
 * and at a 100-damage hit: armour 40 → 20 remaining → flat 10 → taken 10 (90 %).
 *
 * ## Who it applies to
 *
 * **Everyone** — players *and* mobs (`build=cleanup27`).
 *
 * The first revision gated this on players, reasoning that mob armour grows with level and a flat
 * reduction would trivialise low-damage weapons. The author's reply: *"都 40 级怪物了玩家怎么可能用木剑"* —
 * i.e. a mob that has reached the armour wall has also reached the stage where a wooden sword is not the
 * answer, so letting the overflow armour matter is the point, not an exploit. The 75 % flat ceiling is
 * what keeps it bounded.
 */
object ArmorCurve {

    /** Vanilla's wall. At and below it the vanilla formula is used unchanged. */
    const val VANILLA_CAP = 20.0f

    /** Flat damage removed per armour point above [VANILLA_CAP], per hit. */
    const val FLAT_PER_POINT = 0.5f

    /** The flat part never removes more than this fraction of what survived the percentage. */
    const val MAX_FLAT_FRACTION = 0.75f

    /** Vanilla's percentage divisor (documented for the tests; vanilla computes it inline). */
    const val REDUCTION_DIVISOR = 25.0f

    /**
     * The flat part for [armor] against a hit that survived the percentage with [remaining] damage.
     *
     * Returns `0` at or below the wall, so vanilla's behaviour is reproduced exactly there.
     */
    fun flatReduction(armor: Float, remaining: Float): Float {
        if (armor <= VANILLA_CAP || remaining <= 0.0f) return 0.0f
        val requested = (armor - VANILLA_CAP) * FLAT_PER_POINT
        return minOf(requested, remaining * MAX_FLAT_FRACTION)
    }

    /**
     * Full composite: damage taken after the percentage ([effectiveArmor], already damage-adjusted and
     * clamped to the wall) **and** the flat overflow part.
     *
     * Kept as one function so the gametest pins the whole model rather than a piece of it.
     */
    fun damageTaken(damage: Float, armor: Float, effectiveArmor: Float): Float {
        if (damage <= 0.0f) return 0.0f
        val percent = minOf(effectiveArmor, VANILLA_CAP) / REDUCTION_DIVISOR
        val remaining = damage * (1.0f - percent)
        return (remaining - flatReduction(armor, remaining)).coerceAtLeast(0.0f)
    }

    /** Convenience for docs/tests: reduction fraction at [armor] for a hit of [damage]. */
    fun reductionFor(armor: Float, damage: Float = 100.0f): Float {
        if (damage <= 0.0f) return 0.0f
        val taken = damageTaken(damage, armor, minOf(armor, VANILLA_CAP))
        return 1.0f - taken / damage
    }

    /**
     * Every living entity — see the "Who it applies to" note in the class KDoc.
     *
     * Kept as a predicate rather than deleted so the call site in `DamageUtilMixin` reads as
     * "is this an entity we reform?" instead of an unexplained unconditional block, and so a future
     * whitelist/blacklist (e.g. bosses with hand-tuned defence) has one obvious home.
     */
    fun appliesTo(entity: LivingEntity): Boolean = true
}
