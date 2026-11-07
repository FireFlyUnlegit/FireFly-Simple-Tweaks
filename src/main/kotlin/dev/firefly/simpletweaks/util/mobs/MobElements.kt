package dev.firefly.simpletweaks.util.mobs

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.core.config.MobEnhancementConfig
import dev.firefly.simpletweaks.util.elements.ElementType
import net.minecraft.entity.mob.MobEntity
import net.minecraft.util.math.random.Random
import net.minecraft.world.LocalDifficulty

/**
 * Implemented by `MobLevelMixin` on `MobEntity`: the mob's **attack element** ordinal, or
 * [ElementType.NONE_ORDINAL].
 *
 * Separate from [LevelledMob] rather than folded into it because the two are independent features with
 * independent config switches, and a mob can plausibly have one without the other (a levelled physical
 * zombie, or an unlevelled fire spider if the level switch is off).
 *
 * The value is an `Int` ordinal rather than an [ElementType] reference because it has to survive NBT and
 * is read from a mixin, which cannot hold a Kotlin enum field without dragging the enum class across the
 * mixin boundary.
 */
interface ElementalMob {
    var mobElement: Int
}

/**
 * Gives some mobs one random element: their melee hits are partly converted into it, and they resist it.
 *
 * ## Why this is a property of the mob, not of a damage source
 * There is no vanilla concept of "this zombie is a fire zombie". The only place that can carry the
 * decision is the entity, so it is stored there (persistently — a mob must keep its element across a
 * chunk reload, exactly like its level).
 *
 * ## The four knobs
 * | constant | value | meaning |
 * |---|---|---|
 * | [ELEMENT_CHANCE] | `0.35` | base chance per spawn, × `clampedLocalDifficulty` |
 * | [MAX_CHANCE] | `0.75` | cap, so a third of mobs stay plain even at max difficulty |
 * | [CONVERSION_FRACTION] | `0.40` | share of the mob's melee hit turned into its element |
 * | [BONUS_SCALE] | `0.50` | extra elemental damage, as a share of the converted part |
 * | [RESISTANCE] | `0.30` | flat reduction to incoming damage of its own element |
 * | [RESISTANCE_PER_LEVEL] | `0.03` | extra reduction per mob level above 1 |
 *
 * The conversion shape mirrors the player's `xx_conversion` (cancel a share, re-deal it as the element,
 * add a bonus) so the two read the same on the receiving end — and so the player's own
 * `xx_protection` is the correct counter to a mob's elemental attack, with no special-casing.
 *
 * ## Cost
 * Assignment happens once per spawn. The attack path runs on the mob's hits only (already inside the
 * player-oriented element handler, which now branches on attacker type), and the resistance path adds one
 * `instanceof` plus one int compare to `onLivingDamage`. No per-tick work.
 */
object MobElements {

    /**
     * Chance at difficulty **0**, rising to [ELEMENT_CHANCE_MAX].
     *
     * ⚠️ Was `0.35 × clampedLocalDifficulty`, which in a real session measured `chance=0.04` — i.e. the
     * feature was present but effectively invisible. See `MobEnchantments.ARMOR_CHANCE_MIN` for the log
     * evidence; the same mistake existed in three places.
     */
    const val ELEMENT_CHANCE_MIN = 0.15f

    /** Chance at [DIFFICULTY_FOR_FULL_CHANCE] and above. */
    const val ELEMENT_CHANCE_MAX = 0.60f

    /** Same threshold as the enchant side, so both features appear together as difficulty rises. */
    const val DIFFICULTY_FOR_FULL_CHANCE = 2.0f
    const val CONVERSION_FRACTION = 0.40f
    const val BONUS_SCALE = 0.50f
    const val RESISTANCE = 0.30f
    const val RESISTANCE_PER_LEVEL = 0.03f

    /** Hard ceiling on resistance, reached at level 11 — above [MobLevels.MAX_LEVEL] on purpose. */
    private const val MAX_RESISTANCE = 0.60f

    /**
     * Assigns an element to [mob] once, at spawn. Idempotent, like [MobLevels.ensureLevel] — this is
     * called from the same per-slot injector that fires up to five times per spawn.
     */
    fun tryAssign(mob: MobEntity, random: Random, effectiveDifficulty: Float) {
        if (!MobEnhancementConfig.enabled) return
        if (!MobEnhancementConfig.elementsEnabled) return
        val elemental = mob as? ElementalMob ?: return
        if (elemental.mobElement != ElementType.NONE_ORDINAL) return

        val t = (effectiveDifficulty / DIFFICULTY_FOR_FULL_CHANCE).coerceIn(0f, 1f)
        val chance = ELEMENT_CHANCE_MIN + (ELEMENT_CHANCE_MAX - ELEMENT_CHANCE_MIN) * t
        if (random.nextFloat() >= chance) {
            // Debug-gated, but it exists so "no element" and "the hook never ran" are distinguishable —
            // `AGENTS.md` §4.4. Without it a plain mob and a broken seam look identical in the log.
            FSTLog.log("MobElement") {
                "mob=${mob.type.name.string}, chance=${"%.2f".format(chance)}, " +
                    "difficulty=${"%.2f".format(effectiveDifficulty)}, " +
                    "outcome=mob-elemental-roll-failed"
            }
            return
        }

        val element = ElementType.ATTACKABLE[random.nextInt(ElementType.ATTACKABLE.size)]
        elemental.mobElement = element.ordinal

        FSTLog.log("MobElement") {
            "mob=${mob.type.name.string}, element=${element.id}, " +
                "chance=${"%.2f".format(chance)}, " +
                "difficulty=${"%.2f".format(effectiveDifficulty)}, " +
                "outcome=mob-elemental"
        }
    }

    /** The mob's element, or `null` when it is a plain mob. */
    fun elementOf(mob: MobEntity): ElementType? =
        (mob as? ElementalMob)?.let { ElementType.ofOrdinal(it.mobElement) }

    /**
     * Damage reduction for [element] on [mob]: its own element only, scaled by level.
     *
     * Returns `0` for a plain mob and for every element other than its own — the counter to a
     * *specific* element is the player's `xx_protection`; this is the mirror image, on the mob's side.
     */
    fun resistanceFor(mob: MobEntity, element: ElementType): Float {
        if (!MobEnhancementConfig.enabled) return 0f
        if (!MobEnhancementConfig.elementsEnabled) return 0f
        val own = elementOf(mob) ?: return 0f
        if (own != element) return 0f
        val level = (mob as? LevelledMob)?.mobLevel ?: 1
        val steps = (level - 1).coerceAtLeast(0)
        return (RESISTANCE + RESISTANCE_PER_LEVEL * steps).coerceAtMost(MAX_RESISTANCE)
    }
}
