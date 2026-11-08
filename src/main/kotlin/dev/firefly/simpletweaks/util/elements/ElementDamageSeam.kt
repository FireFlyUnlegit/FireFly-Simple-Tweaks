package dev.firefly.simpletweaks.util.elements

import dev.firefly.simpletweaks.compat.FSTLog
import net.minecraft.entity.LivingEntity

/**
 * Last chance to scale **elemental damage the mod is about to deal** — a seam, not a table.
 *
 * ## Why a seam instead of a call into an enchantment
 * `ElementCombat.enqueueElemental` is the single funnel for every elemental rider (conversion, reaction,
 * shield bash, the multitude copy, 万法's counter …). An enchantment that wants to make that damage
 * bigger has to be consulted there, but the engine must not import enchantment ids: that coupling is
 * exactly what `ReactionSeam` was introduced to remove, and the project has been bitten by "what does
 * this engine do for which enchantment" enough times.
 *
 * So the engine asks *this* object and knows nothing about who is behind it. `resonance_core` is the
 * first (and currently only) listener.
 *
 * ## Contract
 * - Listeners run **synchronously** inside `enqueueElemental`, i.e. on the thread that is resolving the
 *   hit, before the amount is validated and queued.
 * - A listener returns the **new** amount. Returning the input unchanged is the "no opinion" answer.
 * - A listener must not deal damage or queue anything (that would re-enter the pipeline it is called
 *   from); it may only scale the number it was given.
 * - A throwing listener is logged and ignored, so a broken enchantment cannot stop damage from landing.
 */
object ElementDamageSeam {

    fun interface Modifier {
        fun modify(
            attacker: LivingEntity?,
            target: LivingEntity,
            element: ElementType,
            amount: Float,
        ): Float
    }

    private val modifiers = ArrayList<Modifier>(2)

    fun register(modifier: Modifier) {
        if (modifiers.none { it === modifier }) modifiers += modifier
    }

    /** The final amount: [amount], passed through every registered modifier in registration order. */
    fun modify(attacker: LivingEntity?, target: LivingEntity, element: ElementType, amount: Float): Float {
        if (modifiers.isEmpty()) return amount
        var current = amount
        for (modifier in modifiers) {
            current = try {
                modifier.modify(attacker, target, element, current)
            } catch (t: Throwable) {
                FSTLog.log("ElementDamageSeam") {
                    "element=${element.id}, amount=$current, error=$t, outcome=modifier-failed"
                }
                current
            }
            // A modifier must never be able to produce a non-finite amount: `enqueueElemental` rejects
            // those, but rejecting them *after* an enchantment multiplied them hides the culprit.
            if (!current.isFinite()) {
                FSTLog.log("ElementDamageSeam") {
                    "element=${element.id}, amount=$current, outcome=modifier-produced-non-finite"
                }
                return 0f
            }
        }
        return current
    }
}
