package dev.firefly.simpletweaks.enchantments.handlers.legendary

import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.compat.event.CriticalHitEvent
import dev.firefly.simpletweaks.compat.event.EventPriority
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.compat.setCrit
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.ModEnchantmentKeys
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import kotlin.random.Random.Default.nextFloat

/**
 * 1.21 port of `enchantments/handlers/legendary/EnchantCritHandler.kt` (21 lines).
 *
 * Line-by-line mapping from 1.12.2:
 * | 1.12.2                                       | 1.21.1                                                       |
 * |----------------------------------------------|--------------------------------------------------------------|
 * | `net.minecraftforge...CriticalHitEvent`      | `compat.event.CriticalHitEvent`                              |
 * | `net.minecraftforge...EventPriority`         | `compat.event.EventPriority`                                 |
 * | `e.entityLiving.heldItemMainhand`            | `e.entityLiving.mainHandStack` (`method_6047`)               |
 * | `EnchantCrit` (Enchantment object)           | `ModEnchantmentKeys.CRIT` (RegistryKey)                      |
 * | `util.setCrit` / `util.invalid`              | `compat.setCrit` / `compat.invalid`                          |
 *
 * Unlike the other two crit handlers this one is on `HIGHEST` and reads only the item — but it is the
 * one that *creates* a crit (`setCrit(true)`), which also adds the vanilla `0.5` multiplier so the
 * forced crit lands on exactly `1.5`. The `!e.isVanillaCritical` guard keeps it from stacking on a
 * swing that already crits.
 */
object EnchantCritHandler : Listenable {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onCrit(e: CriticalHitEvent) {
        if (e.invalid) return

        val lvl = getItemSpecificEnchantLevel(e.entityLiving.mainHandStack, ModEnchantmentKeys.CRIT)
        val roll = nextFloat()
        if (lvl > 0 && roll <= .1 * lvl && !e.isVanillaCritical) {
            e.setCrit(true)
            STLog.log("Crit") {
                "player=${e.entityLiving.name.string}, target=${e.target.name.string}, lvl=$lvl, " +
                    "roll=$roll, threshold=${.1 * lvl}, vanillaCritical=${e.isVanillaCritical}, " +
                    "damageModifier=${e.damageModifier}, outcome=forced-crit"
            }
        }
    }
}
