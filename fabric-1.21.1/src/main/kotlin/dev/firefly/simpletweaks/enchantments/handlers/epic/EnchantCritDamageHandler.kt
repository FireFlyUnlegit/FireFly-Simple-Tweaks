package dev.firefly.simpletweaks.enchantments.handlers.epic

import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.compat.event.CriticalHitEvent
import dev.firefly.simpletweaks.compat.event.EventPriority
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.compat.isCrit
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.ModEnchantmentKeys
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel

/**
 * 1.21 port of `enchantments/handlers/epic/EnchantCritDamageHandler.kt` (25 lines).
 *
 * Line-by-line mapping from 1.12.2:
 * | 1.12.2                                  | 1.21.1                                          |
 * |-----------------------------------------|-------------------------------------------------|
 * | `net.minecraftforge...CriticalHitEvent` | `compat.event.CriticalHitEvent`                 |
 * | `e.entityLiving.heldItemMainhand`       | `e.entityLiving.mainHandStack` (`method_6047`)  |
 * | `util.isCrit` / `util.invalid`          | `compat.isCrit` / `compat.invalid`              |
 * | `EnchantCritDamage` (Enchantment)       | `ModEnchantmentKeys.CRIT_DAMAGE` (RegistryKey)  |
 *
 * The three-step bonus ladder (`+0.1/level`, then `+0.05` from level 2, then `+0.025` from level 6)
 * is carried over verbatim — it is the same ladder `EnchantDoubleCritHandler` folds into its own
 * bonus, which is why both read `CRIT_DAMAGE`.
 */
object EnchantCritDamageHandler : Listenable {

    // ⚠️ `HIGH`, deliberately NOT `HIGHEST`. `EnchantCritHandler` is the only `HIGHEST` listener on
    // `CriticalHitEvent`, so it always runs first and its forced crit is visible to the `e.isCrit`
    // test below. Expressing the order through the priority (instead of relying on this handler being
    // registered right after `EnchantCritHandler` in `EnchantmentManager.handlerList`) is what makes
    // the crit chain survive a move to `@ModEnchantment`, whose handlers are appended at the end of
    // the bus. Ordering among the two `HIGH` listeners (this one, then `EnchantDoubleCritHandler`) is
    // still registration order -- keep this handler ahead of DoubleCrit in `handlerList`.
    @SubscribeEvent(priority = EventPriority.HIGH)
    fun onCrit(e: CriticalHitEvent) {
        if (e.invalid) return

        val lvl = getItemSpecificEnchantLevel(e.entityLiving.mainHandStack, ModEnchantmentKeys.CRIT_DAMAGE)
        if (lvl > 0 && e.isCrit) {
            val before = e.damageModifier

            e.damageModifier += (.1 * lvl).toFloat()

            if (lvl >= 2) e.damageModifier += (.05f * (lvl - 2))
            if (lvl >= 6) e.damageModifier += (.025f * (lvl - 6))

            STLog.log("CritDamage") {
                "player=${e.entityLiving.name.string}, target=${e.target.name.string}, lvl=$lvl, " +
                    "vanillaCritical=${e.isVanillaCritical}, damageModifier=$before->${e.damageModifier}"
            }
        }
    }
}
