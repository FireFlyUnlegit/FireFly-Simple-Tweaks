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
 * One elemental reaction: the **aura** already on the target, the **trigger** delivered by the current
 * hit, and what the two do together.
 *
 * ## The two families (why `damageBonus` is gone)
 * The previous model had a single knob, `damageBonus` = "deal this fraction of the hit again". That is
 * neither of the two things a reference reaction system distinguishes, and the difference is the whole
 * point:
 *
 * | family | 中文 | how the damage is produced | scales with |
 * |---|---|---|---|
 * | [amplify] | 增幅反应 | multiplies **this hit** | the hit's own damage, crit and all |
 * | [transformativeCoeff] | 剧变反应 | a **separate**, flat hit | attacker level + mastery only, never crits |
 *
 * So `melt` (frost aura, fire trigger) is `amplify = 2.0`: the sword swing itself is doubled, exactly
 * like 融化. `conduct` is a transformative coefficient: it deals its own number, which does **not**
 * grow when the player finds a better sword — only when they level up or stack mastery. That is what
 * keeps a transformative reaction from being a second copy of the weapon.
 *
 * ## What else a reaction may do
 * Effects are unchanged from the previous revision and all remain **relative** (fractions of the hit or
 * of the target's armour), never flat numbers, for the reason recorded there: `+4` decides a
 * wooden-sword fight and is noise against a netherite one.
 *
 * ## Aura cost
 * Every reaction consumes aura units. [triggerUnits] is how much the triggering hit carries, and
 * [ElementReactions.dampening] doubles the cost when the trigger element **counters** the aura's
 * element (水克火 / 火克冰 / 水·火·冰·雷克自然). Consumption is resolved in [ElementMarks]: the aura
 * keeps whatever is left, the trigger itself never lingers (后手不残留).
 */
data class ElementReaction(
    /** The element already on the target (from an earlier hit). */
    val mark: ElementType,
    /** The element of the hit that triggers it. */
    val trigger: ElementType,
    /** Display name key suffix: `reaction.simple_tweaks.<key>`. */
    val key: String,
    /** 增幅: multiply the triggering hit's damage by this. `0` = not an amplifying reaction. */
    val amplify: Float = 0f,
    /** 剧变: flat damage = base(level, mastery) × this coefficient. `0` = not transformative. */
    val transformativeCoeff: Float = 0f,
    /** Aura units the triggering hit carries (see [ElementAuras]); strong triggers survive weak auras. */
    val triggerUnits: Float = ElementAuras.WEAK,
    /** 激化-style: instead of damage, mark the target as "quickened" for [quickenTicks]. */
    val quicken: Boolean = false,
    val quickenTicks: Int = 120,
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
    /** Amplifying reactions scale the hit; everything else is either flat damage or utility. */
    val isAmplifying: Boolean get() = amplify > 0f
}

/**
 * The reaction table.
 *
 * Every pair is declared **both ways** (mark A + trigger B, mark B + trigger A) whenever the reaction
 * exists in both directions, because which element the player happens to land second must not decide
 * whether there is a reaction at all. Where the two directions differ they genuinely differ — 火打冰 vs
 * 冰打火 are different multipliers in the reference, and they are different rows here.
 *
 * ```
 *           trigger: PHYS  FIRE      FROST     LIGHTNING  WATER     NATURE    DARK        LIGHT
 *  mark PHYS     -      .          .         .          .         .        backstab    smother
 *  mark FIRE     .      -        vanish×1.5    .       steam×2.0  burn       scorch      sanctify
 *  mark FROST    .   melt×2.0       -       overload      freeze   .           .         eclipse
 *  mark LIGHTNING.      .         overload      -       conduct   quicken      .           .
 *  mark WATER    .   steam×1.5    brittle   conduct       -      bloom        .         purify
 *  mark NATURE   .      burn        .       quicken     bloom      -         blight       bloom
 *  mark DARK  backstab scorch       .         .          .       blight        -       annihilate
 *  mark LIGHT smother sanctify    eclipse      .        purify     bloom   annihilate       -
 * ```
 * `.` = no reaction; `-` = same element (impossible: the trigger is resolved before the aura is
 * overwritten).
 */
object ElementReactions {

    private val TABLE: Map<Pair<ElementType, ElementType>, ElementReaction> = buildList {
        // ---- 增幅 (amplifying): the swing itself is multiplied -----------------------------------
        // 火打冰 = 融化 ×2.0 (the reference's strongest amplifier).
        add(ElementReaction(FROST, FIRE, "melt", amplify = 2.0f, triggerUnits = ElementAuras.STRONG))
        // 冰打火 = 融化 ×1.5.
        add(ElementReaction(FIRE, FROST, "vanish", amplify = 1.5f))
        // 水打火 = 蒸发 ×2.0, 火打水 = 蒸发 ×1.5. Both keep the `steam` key so the existing lang and
        // the floating label stay valid.
        add(ElementReaction(FIRE, WATER, "steam", amplify = 2.0f, triggerUnits = ElementAuras.STRONG))
        add(ElementReaction(WATER, FIRE, "steam", amplify = 1.5f))

        // ---- 剧变 (transformative): its own flat hit --------------------------------------------
        // 感电 — water ↔ lightning. The reference's 1 s per-target cooldown is enforced in
        // `ElementMarks` (`TRANSFORMATIVE_ICD`), not here.
        add(
            ElementReaction(
                LIGHTNING, WATER, "conduct",
                transformativeCoeff = 1.25f, armorShredFraction = 0.40f, duration = 120,
            ),
        )
        add(
            ElementReaction(
                WATER, LIGHTNING, "conduct",
                transformativeCoeff = 1.15f, armorShredFraction = 0.40f, duration = 120,
            ),
        )
        // 超载 — frost ↔ lightning. Big flat hit, and ice that shatters shreds armour.
        add(
            ElementReaction(
                FROST, LIGHTNING, "overload",
                transformativeCoeff = 1.60f, armorShredFraction = 0.20f, duration = 80,
            ),
        )
        add(ElementReaction(LIGHTNING, FROST, "overload", transformativeCoeff = 1.50f, slowTicks = 40))

        // 燃烧 — fire on a nature aura, and 熄灭 the other way round.
        add(ElementReaction(NATURE, FIRE, "burn", transformativeCoeff = 1.35f))
        add(ElementReaction(FIRE, NATURE, "extinguish", transformativeCoeff = 0.90f, slowTicks = 80, slownessAmplifier = 1))

        // 湮灭 — light ↔ dark. The reference's "mutual annihilation": flat damage plus blindness.
        add(ElementReaction(DARK, LIGHT, "annihilate", transformativeCoeff = 1.70f, blindTicks = 80, healAttacker = 0.25f))
        add(ElementReaction(LIGHT, DARK, "annihilate", transformativeCoeff = 1.70f, blindTicks = 80, healAttacker = 0.25f))

        // 圣化 / 涤净 — light with fire or water. 涤净 is the sustain pair: tiny coefficient, big heal.
        add(ElementReaction(FIRE, LIGHT, "sanctify", transformativeCoeff = 1.20f, healAttacker = 0.10f))
        add(ElementReaction(LIGHT, FIRE, "sanctify", transformativeCoeff = 1.20f, healAttacker = 0.10f))
        add(ElementReaction(WATER, LIGHT, "purify", transformativeCoeff = 0.60f, healAttacker = 0.30f))
        add(ElementReaction(LIGHT, WATER, "purify", transformativeCoeff = 0.60f, healAttacker = 0.30f))

        // 灼魂 / 枯萎 / 日蚀 — fire·nature·frost paired with dark or light.
        add(ElementReaction(FIRE, DARK, "scorch", transformativeCoeff = 1.45f, blindTicks = 40))
        add(ElementReaction(DARK, FIRE, "scorch", transformativeCoeff = 1.45f, blindTicks = 40))
        add(ElementReaction(NATURE, DARK, "blight", transformativeCoeff = 1.10f, slowTicks = 60, slownessAmplifier = 1))
        add(ElementReaction(DARK, NATURE, "blight", transformativeCoeff = 1.10f, slowTicks = 60, slownessAmplifier = 1))
        add(ElementReaction(FROST, LIGHT, "eclipse", transformativeCoeff = 0.80f, blindTicks = 60, slowTicks = 40))
        add(ElementReaction(LIGHT, FROST, "eclipse", transformativeCoeff = 0.80f, blindTicks = 60, slowTicks = 40))

        // ---- utility / control pairs (zero or token damage) --------------------------------------
        // 冻结 — water on an ice aura. No damage at all, it just locks the target down.
        add(ElementReaction(FROST, WATER, "freeze", freezeTicks = 200))
        // 脆化 — frost on a wet aura. Small hit, big shred.
        add(ElementReaction(WATER, FROST, "brittle", transformativeCoeff = 0.50f, armorShredFraction = 0.20f, duration = 100))
        // 绽放 — nature with water. Pure control, heavy and long.
        add(ElementReaction(WATER, NATURE, "bloom", slowTicks = 140, slownessAmplifier = 3))
        add(ElementReaction(NATURE, WATER, "bloom", slowTicks = 140, slownessAmplifier = 3))
        // 绽放（光）— nature with light: a small heal instead of a root.
        add(ElementReaction(NATURE, LIGHT, "bloom", transformativeCoeff = 0.30f, healAttacker = 0.15f))
        add(ElementReaction(LIGHT, NATURE, "bloom", transformativeCoeff = 0.30f, healAttacker = 0.15f))

        // ---- 激化 (quicken) — nature ↔ lightning ------------------------------------------------
        // The reference's dendro+electro: no damage by itself, it puts the target in a "quickened"
        // state, and every following nature/lightning hit gets a flat bonus. `ElementMarks` owns the
        // state; the numbers live there too (`QUICKEN_BONUS_PER_LEVEL`).
        add(ElementReaction(NATURE, LIGHTNING, "quicken", quicken = true, triggerUnits = ElementAuras.STRONG))
        add(ElementReaction(LIGHTNING, NATURE, "quicken", quicken = true, triggerUnits = ElementAuras.STRONG))

        // ---- physical is special ----------------------------------------------------------------
        // A physical strike on a dark aura: 背刺, the biggest single-target flat hit in the table.
        add(ElementReaction(DARK, PHYSICAL, "backstab", transformativeCoeff = 1.75f))
        // A physical strike on a light aura: 闷击, blinds and barely hurts.
        add(ElementReaction(LIGHT, PHYSICAL, "smother", transformativeCoeff = 0.55f, blindTicks = 60))
    }.associateBy { it.mark to it.trigger }

    /** The reaction for an aura of [mark] triggered by a hit of [trigger], or `null`. */
    fun of(mark: ElementType, trigger: ElementType): ElementReaction? = TABLE[mark to trigger]

    /** Every declared reaction, for logging and for the acceptance checklist. */
    val ALL: List<ElementReaction> get() = TABLE.values.toList()

    /**
     * 克制链 — how much aura the trigger eats for one unit it carries.
     *
     * `2.0` means "this trigger counters that aura": 1 unit of water strips 2 units of fire, 1 unit of
     * fire strips 2 of frost, and water/fire/frost/lightning all counter nature.
     *
     * Mirrors the reference's 消耗比例, restricted to the eight elements this mod has (the mod has no
     * anemo/geo, so the "counters nature" clause replaces 风/岩).
     */
    fun dampening(mark: ElementType, trigger: ElementType): Float = when {
        trigger == WATER && mark == FIRE -> 2.0f
        trigger == FIRE && mark == FROST -> 2.0f
        trigger == FROST && mark == WATER -> 2.0f
        trigger in COUNTERS_NATURE && mark == NATURE -> 2.0f
        else -> 1.0f
    }

    private val COUNTERS_NATURE = setOf(WATER, FIRE, FROST, LIGHTNING)
}
