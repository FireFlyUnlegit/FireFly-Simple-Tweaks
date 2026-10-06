package dev.firefly.simpletweaks.enchantments.handlers.legendary

import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.compat.event.CriticalHitEvent
import dev.firefly.simpletweaks.compat.event.EventPriority
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.compat.setCrit
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
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
 * | `EnchantCrit` (Enchantment object)           | `GeneratedEnchantments.CRIT` (RegistryKey)                   |
 * | `util.setCrit` / `util.invalid`              | `compat.setCrit` / `compat.invalid`                          |
 *
 * Unlike the other two crit handlers this one is on `HIGHEST` and reads only the item — but it is the
 * one that *creates* a crit (`setCrit(true)`), which also adds the vanilla `0.5` multiplier so the
 * forced crit lands on exactly `1.5`. The `!e.isVanillaCritical` guard keeps it from stacking on a
 * swing that already crits.
 *
 * Being the only `HIGHEST` listener on `CriticalHitEvent` is also what makes this handler safe to
 * declare with `@ModEnchantment`: KSP-declared handlers are appended *after* `handlerList` (see
 * `EnchantmentManager.initHandlers`), so if this were still `HIGH` alongside `EnchantCritDamageHandler`
 * it would end up running second and silently disable CritDamage's `e.isCrit` branch. Do not lower it.
 */
@ModEnchantment(
    id = "crit",
    category = EnchantCategory.LEGENDARY,
    type = EnchantType.SWORD,
    maxLevel = 10,
    weight = 2,
    anvilCost = 10,
    minCostBase = 20,
    minCostPerLevel = 5,
    supportedItems = "#minecraft:enchantable/sword",
    slots = [EnchantSlot.MAINHAND],
    // `+0.4` per level == 1.12.2's `itemExtraDamage` for a LEGENDARY sword enchantment
    // (0.2 + rarity/20 = 0.2 + 0.2). Reproduces the `minecraft:damage` block of the old crit.json
    // byte for byte; this is the parameter `crit` could not be migrated without.
    damagePerLevel = 0.4,
)
object EnchantCritHandler : Listenable {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onCrit(e: CriticalHitEvent) {
        if (e.invalid) return

        val lvl = getItemSpecificEnchantLevel(e.entityLiving.mainHandStack, GeneratedEnchantments.CRIT)
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
