package dev.firefly.simpletweaks.util.elements

import net.minecraft.entity.LivingEntity

/**
 * Fires whenever an **elemental reaction actually resolves**.
 *
 * ## Why a seam instead of calls inside `ElementMarks`
 * The engine knows *that* a reaction happened; only an enchantment knows what to do about it
 * (`elemental_spread` attaches the same element to nearby enemies). Keeping the hook in a seam means the
 * engine stays free of enchantment ids — the same shape as `ArrowDamageSeam`, and for the same reason:
 * engine files were already carrying `GeneratedEnchantments` lookups in three places for damage scaling,
 * which made "what does this engine do for which enchantment" impossible to answer.
 *
 * Listeners run **after** the reaction has already been applied (damage queued, status effects set), inside
 * the original damage call — so a listener must not deal damage without the auxiliary flag, or it re-enters
 * the on-hit pipeline.
 */
object ReactionSeam {

    /**
     * @param attacker the player credited with the reaction, or `null` when it came from the environment.
     * @param target the entity whose aura was consumed.
     * @param element the **trigger** element (the one that arrived), not the aura that was already there.
     */
    fun interface Listener {
        fun onReaction(attacker: LivingEntity?, target: LivingEntity, element: ElementType)
    }

    private val listeners = ArrayList<Listener>(2)

    fun register(listener: Listener) {
        if (listeners.none { it === listener }) listeners += listener
    }

    fun fire(attacker: LivingEntity?, target: LivingEntity, element: ElementType) {
        if (listeners.isEmpty()) return
        for (listener in listeners) {
            try {
                listener.onReaction(attacker, target, element)
            } catch (t: Throwable) {
                // A listener must never be able to break the damage that triggered it.
                dev.firefly.simpletweaks.compat.FSTLog.log("ReactionSeam") {
                    "outcome=listener-failed, element=${element.id}, error=$t"
                }
            }
        }
    }
}
