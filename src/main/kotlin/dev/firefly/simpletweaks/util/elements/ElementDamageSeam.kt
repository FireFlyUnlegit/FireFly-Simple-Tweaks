package dev.firefly.simpletweaks.util.elements

import dev.firefly.simpletweaks.compat.FSTLog
import net.minecraft.entity.LivingEntity

/**
 * Hook for scaling elemental damage about to be dealt. The engine consults it instead of importing an
 * enchantment id; listeners may only scale the amount, never deal damage or queue anything.
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
