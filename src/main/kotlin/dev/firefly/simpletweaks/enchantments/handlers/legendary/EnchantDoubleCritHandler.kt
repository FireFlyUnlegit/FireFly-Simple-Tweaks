package dev.firefly.simpletweaks.enchantments.handlers.legendary

import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.compat.event.CriticalHitEvent
import dev.firefly.simpletweaks.compat.event.EventPriority
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.compat.isCrit
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.ModEnchantmentKeys
import dev.firefly.simpletweaks.util.attackCharge
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import kotlin.random.Random.Default.nextFloat

/**
 * 1.21 port of `enchantments/handlers/legendary/EnchantDoubleCritHandler.kt` (37 lines).
 *
 * Line-by-line mapping from 1.12.2:
 * | 1.12.2                                       | 1.21.1                                                        |
 * |----------------------------------------------|---------------------------------------------------------------|
 * | `net.minecraftforge...CriticalHitEvent`      | `compat.event.CriticalHitEvent`                               |
 * | `living.safeGetCooledAttackStrength()`       | `e.attackCharge` — the value captured at the HEAD of `PlayerEntity#attack`, which is what the 1.12.2 `MixinEntityPlayer` `@Redirect` captured |
 * | `e.entityLiving.heldItemMainhand`            | `e.entityLiving.mainHandStack` (`method_6047`)                |
 * | `EnchantDoubleCrit` / `EnchantCritDamage`    | `ModEnchantmentKeys.DOUBLE_CRIT` / `ModEnchantmentKeys.CRIT_DAMAGE` |
 * | `util.isCrit` / `util.invalid`               | `compat.isCrit` / `compat.invalid`                            |
 *
 * ⚠️ **DEVIATION — please review by hand (the only one in this file).**
 * 1.12.2 wrote `val living = e.entityLiving ?: return` because `CriticalHitEvent`'s inherited
 * `getEntityLiving()` was nullable in Kotlin's view. The port's `entityLiving` is a non-null
 * `PlayerEntity`, so the elvis is dropped; everything else, including the `0.848` gate and the
 * `0.5f + critDMG` bonus, is unchanged.
 *
 * ⚠️ **Second deviation, and a real bug it fixed.** `living.attackCharge` reads
 * `getAttackCooldownProgress(0.5F)` *live*, and `PlayerEntity#attack` resets the cooldown timer
 * before this event fires — so the live read is ≈0.5 and the `>= 0.848` gate rejected every single
 * swing. `e.attackCharge` is the value captured before the reset, so the gate behaves as it did in
 * 1.12.2. Symptom if it regresses: `DoubleCrit` never appears in the log, ever.
 *
 * The charge gate is what makes this "double" crit: it only fires on a fully charged swing.
 */
object EnchantDoubleCritHandler : Listenable {

    @SubscribeEvent(priority = EventPriority.HIGH)
    fun onCrit(e: CriticalHitEvent) {
        if (e.invalid) return

        val living = e.entityLiving
        val charge = e.attackCharge
        if (charge < 0.848) return

        val lvl = getItemSpecificEnchantLevel(living.mainHandStack, ModEnchantmentKeys.DOUBLE_CRIT)
        val lvl2 = getItemSpecificEnchantLevel(living.mainHandStack, ModEnchantmentKeys.CRIT_DAMAGE)

        if (lvl > 0) {
            val critDMG = (
                (.1f * lvl2) +
                    (if (lvl2 >= 2) (.05f * (lvl2 - 2)) else 0.0f) +
                    (if (lvl2 >= 6) (.025f * (lvl2 - 6)) else 0.0f)
                )
            val roll = nextFloat()
            if (roll <= lvl * .1 && e.isCrit) {
                val before = e.damageModifier
                e.damageModifier += 0.5f + critDMG

                STLog.log("DoubleCrit") {
                    "player=${living.name.string}, target=${e.target.name.string}, lvl=$lvl, " +
                        "critDamageLvl=$lvl2, charge=$charge, roll=$roll, critDMG=$critDMG, " +
                        "damageModifier=$before->${e.damageModifier}"
                }
            }
        }
    }
}
