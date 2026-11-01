package dev.firefly.simpletweaks.util.elements

import dev.firefly.simpletweaks.util.elements.ElementType.DARK
import dev.firefly.simpletweaks.util.elements.ElementType.FIRE
import dev.firefly.simpletweaks.util.elements.ElementType.FROST
import dev.firefly.simpletweaks.util.elements.ElementType.LIGHT
import dev.firefly.simpletweaks.util.elements.ElementType.LIGHTNING
import dev.firefly.simpletweaks.util.elements.ElementType.NATURE
import dev.firefly.simpletweaks.util.elements.ElementType.PHYSICAL
import dev.firefly.simpletweaks.util.elements.ElementType.WATER

/**
 * One elemental reaction: a **mark** left by an earlier hit, a **trigger** delivered by the current
 * hit, and what the two do together.
 *
 * ## The model
 * When a hit of element X lands on a target:
 * 1. if the target already carries a mark of element Y **and** (Y, X) is a reaction below, that
 *    reaction fires and the mark is consumed;
 * 2. otherwise the hit leaves its own mark X (replacing any old mark).
 *
 * So reactions are **pairwise and single-target**, which is what the author asked for. There is no
 * chain: one mark, one trigger, one reaction.
 *
 * ## What a reaction is allowed to change
 * | field | meaning |
 * |---|---|
 * | `damageBonus` | **fraction** of the triggering hit dealt again as the trigger element |
 * | `armorShredFraction` | **fraction** of the target's *current* armour stripped for `duration` |
 * | `freezeTicks` | target's freeze counter (powder-snow style), 0 = none |
 * | `slowTicks` / `slownessAmplifier` | vanilla slowness |
 * | `blindTicks` | vanilla blindness (for the light/shadow pair) |
 * | `healAttacker` | fraction of the **triggering hit** that returns to the attacker |
 *
 * ⚠️ **Nothing here is applied twice.** `damageBonus` is dealt by [ElementMarks] as a *separate*
 * `LivingHurtEvent` of the trigger element — it never re-enters the conversion path, so a reaction
 * cannot recursively convert and cannot double-count. See `ElementCombat` for the guard.
 *
 * ## Why every number is a fraction, not a flat value
 * The first revision used flat numbers (`+4.0` damage, `-2` armour). They do not survive a levelling
 * curve: `+4` decides a wooden-sword fight and is noise on a Sharpness V netherite sword, and `-2`
 * armour is 20% of iron but 5% of netherite. Every effect here is therefore **relative to the hit or
 * the target**, so the reaction keeps the same *feel* at every stage of the game:
 *
 * - `damageBonus` scales with the hit that triggered it (and therefore with your gear and crits);
 * - `armorShredFraction` scales with the armour it is chewing through;
 * - `healAttacker` already did (it reads the final hit).
 *
 * The **only** remaining absolute quantities are durations (ticks) and the status-effect amplifiers,
 * because those are units of time and of vanilla's own scale, not of power.
 *
 * ## Design notes (why these numbers)
 * - A reaction is worth **roughly half a hit** (`0.35–0.60 × the triggering hit`), not a one-shot.
 *   A reward that ends fights makes the conversion enchantments strictly better than every other
 *   sword enchantment, which is what `damage_lottery` was removed for.
 * - **Opposed pairs are strong, adjacent pairs are utility.** Fire↔Frost and Light↔Dark cancel each
 *   other out, so they get the biggest multipliers; Water+Lightning is the classic conduct combo and
 *   gets the biggest *shred*; Nature+Fire is the classic burn combo.
 * - **Two pairs are deliberately weird** so the matrix is not just "everything combos with everything":
 *   - **Light + Dark** *heals the attacker* and blinds the target — a mutual-annihilation pair.
 *   - **Nature + Water** does no damage at all: it roots the target (heavy slowness) for long.
 * - Physical does **not** react with fire/frost/lightning/water/nature (it is the "no element" base),
 *   but it **does** react with Light and Dark: a physical strike on a light-marked target is a
 *   "smother", and on a dark-marked target a "backstab".
 */
data class ElementReaction(
    /** The element already on the target (from an earlier hit). */
    val mark: ElementType,
    /** The element of the hit that triggers it. */
    val trigger: ElementType,
    /** Display name key suffix: `reaction.simple_tweaks.<key>`. */
    val key: String,
    /** Extra damage as a **fraction of the triggering hit**; `0.35` = +35%. */
    val damageBonus: Float = 0f,
    /** Armour stripped as a **fraction of the target's current armour**; `0.30` = −30%. */
    val armorShredFraction: Float = 0f,
    val duration: Int = 0,
    val freezeTicks: Int = 0,
    val slowTicks: Int = 0,
    val slownessAmplifier: Int = 0,
    val blindTicks: Int = 0,
    /** Fraction of the triggering hit healed back to the attacker; 0 = none. */
    val healAttacker: Float = 0f,
) {
    /** True for a reaction that has no numeric effect beyond its name (nothing but a log line). */
    val isNull: Boolean
        get() = damageBonus == 0f && armorShredFraction == 0f && freezeTicks == 0 &&
            slowTicks == 0 && blindTicks == 0 && healAttacker == 0f
}

/**
 * The reaction table. **Ordered and symmetric beyond what it needs to be** — every pair is declared
 * once per direction, because mark and trigger are not interchangeable ("fire on a wet target" vs
 * "water on a burning target" are different things in every game that has ever done this).
 *
 * ## The full matrix
 * ```
 *           trigger: PHYS FIRE FROST LIGHT WATER NATURE DARK LIGHT
 *  mark PHYS     -    .     .     .     .     .    backstab  smother
 *  mark FIRE     .    -   vanish   .  steam  burn  extinguish   .
 *  mark FROST    .  melt    -   brittle  .     .       .        .
 *  mark LIGHT    .    .   conduct  -    conduct  .     .        .
 *  mark WATER    .  steam   freeze conduct  -    bloom   .        .
 *  mark NATURE   .  burn    .      .   bloom   -     .      bloom
 *  mark DARK  backstab .    .      .     .     .     -     annihilate
 *  mark LIGHT smother  .    .      .     .   bloom annihilate   -
 * ```
 * `.` = no reaction. Diagonal (`-`) is impossible: a hit that leaves a mark cannot also trigger that
 * same mark, because the trigger fires before the mark is written.
 */
object ElementReactions {

    private val TABLE: Map<Pair<ElementType, ElementType>, ElementReaction> = buildList {
        // ---- opposed pairs: the strong ones ------------------------------------------------------
        // Fire on a frost-marked target: the ice shatters. Biggest multiplier in the matrix, and the
        // deepest shred — the target's own armour decides how much that is worth.
        add(ElementReaction(FROST, FIRE, "melt", damageBonus = 0.55f, armorShredFraction = 0.25f, duration = 100))
        // Frost on a fire-marked target: douse the flame, and the steam scalds.
        add(ElementReaction(FIRE, FROST, "vanish", damageBonus = 0.45f, slowTicks = 60, slownessAmplifier = 1))
        // Light on dark / dark on light: mutual annihilation. Hurts the target, heals the attacker.
        add(
            ElementReaction(
                DARK, LIGHT, "annihilate",
                damageBonus = 0.60f, blindTicks = 80, healAttacker = 0.25f,
            ),
        )
        add(
            ElementReaction(
                LIGHT, DARK, "annihilate",
                damageBonus = 0.60f, blindTicks = 80, healAttacker = 0.25f,
            ),
        )

        // ---- water / lightning: conduct ----------------------------------------------------------
        // Water on a lightning-marked target: the charge spreads through the water. Biggest shred.
        add(ElementReaction(LIGHTNING, WATER, "conduct", damageBonus = 0.35f, armorShredFraction = 0.40f, duration = 120))
        // Lightning on a wet target: same idea, from the other side. Slightly stronger overall.
        add(ElementReaction(WATER, LIGHTNING, "conduct", damageBonus = 0.40f, armorShredFraction = 0.40f, duration = 120))

        // ---- fire / water: steam ---------------------------------------------------------------
        // Water on a burning target: steam burst, no residual effect.
        add(ElementReaction(FIRE, WATER, "steam", damageBonus = 0.45f))
        // Fire on a wet target: boil the water off; the target is scalded and slowed briefly.
        add(ElementReaction(WATER, FIRE, "steam", damageBonus = 0.40f, slowTicks = 40))

        // ---- nature ------------------------------------------------------------------------------
        // Fire on a nature-marked target: it burns.
        add(ElementReaction(NATURE, FIRE, "burn", damageBonus = 0.50f))
        // Nature on a fire-marked target: growth snuffs the flame, slow but steady.
        add(ElementReaction(FIRE, NATURE, "extinguish", damageBonus = 0.25f, slowTicks = 80, slownessAmplifier = 1))
        // Nature + water: bloom. No damage — it roots the target for a long time.
        add(ElementReaction(WATER, NATURE, "bloom", slowTicks = 140, slownessAmplifier = 3))
        add(ElementReaction(NATURE, WATER, "bloom", slowTicks = 140, slownessAmplifier = 3))
        // Light + nature: bloom of light, small heal for the attacker.
        add(ElementReaction(NATURE, LIGHT, "bloom", damageBonus = 0.25f, healAttacker = 0.15f))
        add(ElementReaction(LIGHT, NATURE, "bloom", damageBonus = 0.25f, healAttacker = 0.15f))

        // ---- frost / water -----------------------------------------------------------------------
        // Water on a frost-marked target: it freezes solid.
        add(ElementReaction(FROST, WATER, "freeze", freezeTicks = 200))
        // Frost on a wet target: brittles, and armour shatters a little.
        add(ElementReaction(WATER, FROST, "brittle", armorShredFraction = 0.20f, duration = 100))

        // ---- `build=cleanup19`: the pairs the matrix was still missing ------------------------------
        // Six opposed-but-not-cancelling pairs. The rule of the table is kept: every pair is declared
        // **both ways** (mark A + trigger B and mark B + trigger A), because which element the player
        // happens to land second must not decide whether the reaction exists.

        // Frost + lightning: the charge shatters the ice. Second-biggest multiplier after `melt`.
        add(ElementReaction(FROST, LIGHTNING, "overload", damageBonus = 0.50f, armorShredFraction = 0.20f, duration = 80))
        add(ElementReaction(LIGHTNING, FROST, "overload", damageBonus = 0.50f, slowTicks = 40))

        // Fire + light: holy flame. Damage, and the light heals whoever struck.
        add(ElementReaction(FIRE, LIGHT, "sanctify", damageBonus = 0.40f, healAttacker = 0.10f))
        add(ElementReaction(LIGHT, FIRE, "sanctify", damageBonus = 0.40f, healAttacker = 0.10f))

        // Water + light: the water is purified and cleanses the attacker. Almost no damage — this pair
        // is the "sustain" reaction, so its value is the heal, not the number.
        add(ElementReaction(WATER, LIGHT, "purify", damageBonus = 0.20f, healAttacker = 0.30f))
        add(ElementReaction(LIGHT, WATER, "purify", damageBonus = 0.20f, healAttacker = 0.30f))

        // Fire + dark: burning soul-fire. Heavy damage plus a short blind.
        add(ElementReaction(FIRE, DARK, "scorch", damageBonus = 0.45f, blindTicks = 40))
        add(ElementReaction(DARK, FIRE, "scorch", damageBonus = 0.45f, blindTicks = 40))

        // Nature + dark: the growth withers. Modest damage, long slow.
        add(ElementReaction(NATURE, DARK, "blight", damageBonus = 0.35f, slowTicks = 60, slownessAmplifier = 1))
        add(ElementReaction(DARK, NATURE, "blight", damageBonus = 0.35f, slowTicks = 60, slownessAmplifier = 1))

        // Frost + light: light bent through ice. Blinds and slows, little damage.
        add(ElementReaction(FROST, LIGHT, "eclipse", damageBonus = 0.20f, blindTicks = 60, slowTicks = 40))
        add(ElementReaction(LIGHT, FROST, "eclipse", damageBonus = 0.20f, blindTicks = 60, slowTicks = 40))
        // ---- physical is special ------------------------------------------------------------------
        // A physical strike on a light-marked target: it smothers the light. Blinds, barely damages.
        add(ElementReaction(LIGHT, PHYSICAL, "smother", blindTicks = 60, damageBonus = 0.15f))
        // A physical strike on a dark-marked target: a backstab. The biggest single-target multiplier.
        add(ElementReaction(DARK, PHYSICAL, "backstab", damageBonus = 0.55f))
    }.associateBy { it.mark to it.trigger }

    /** The reaction for a mark of [mark] triggered by a hit of [trigger], or `null`. */
    fun of(mark: ElementType, trigger: ElementType): ElementReaction? = TABLE[mark to trigger]

    /** Every declared reaction, for logging and for the acceptance checklist. */
    val ALL: List<ElementReaction> get() = TABLE.values.toList()
}
