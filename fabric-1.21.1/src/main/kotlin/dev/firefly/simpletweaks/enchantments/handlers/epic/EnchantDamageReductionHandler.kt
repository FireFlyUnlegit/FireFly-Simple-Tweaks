package dev.firefly.simpletweaks.enchantments.handlers.epic

import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.compat.event.EventPriority
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.compat.target
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.getArmorEnchantLevel
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment

/**
 * 1.21 port of `enchantments/handlers/epic/EnchantDamageReductionHandler.kt`.
 *
 * Line-by-line mapping from 1.12.2:
 * | 1.12.2                                  | 1.21.1                                                       |
 * |-----------------------------------------|--------------------------------------------------------------|
 * | `net.minecraftforge...LivingHurtEvent`  | `compat.event.LivingHurtEvent`                               |
 * | `net.minecraftforge...EventPriority`    | `compat.event.EventPriority` (annotation keeps `priority = HIGH`) |
 * | `e.target`                              | `compat.target` extension (Forge's `LivingHurtEvent.getEntityLiving()`) |
 * | `e.target.getArmorEnchantLevel(EnchantDamageReduction)` | `getArmorEnchantLevel(GeneratedEnchantments.DAMAGE_REDUCTION)` (already ported) |
 *
 * Every magic number (0.024 / 0.018 / 0.012 / 0.006, the 5/10/15 breakpoints, the 0.9 cap and the
 * `lvl * 0.001f` flat subtraction) is copied verbatim.
 */
@ModEnchantment(
    id = "damage_reduction",
    category = EnchantCategory.EPIC,
    type = EnchantType.ARMOR,
    maxLevel = 5,
    weight = 4,
    anvilCost = 8,
    minCostBase = 10,
    minCostPerLevel = 10,
    supportedItems = "#minecraft:enchantable/armor",
    slots = [EnchantSlot.ARMOR],
    order = 25,
)
object EnchantDamageReductionHandler : Listenable {
    @SubscribeEvent(priority = EventPriority.HIGH)
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        val lvl = e.target.getArmorEnchantLevel(GeneratedEnchantments.DAMAGE_REDUCTION)
        if (lvl > 0) {
            val reduction1 = 0.024f * lvl
            val reduction2 = 0.018f * (lvl - 5).coerceAtLeast(0)
            val reduction3 = 0.012f * (lvl - 10).coerceAtLeast(0)
            val reduction4 = 0.006f * (lvl - 15).coerceAtLeast(0)
            val totalReduction = (reduction1 + reduction2 + reduction3 + reduction4).coerceAtMost(0.9f)
            val damageBefore = e.amount
            e.amount = (e.amount * (1f - totalReduction) - (lvl * 0.001f)).coerceAtLeast(0f)
            STLog.log("DamageReduction") {
                "target=${e.target.name.string}, lvl=$lvl, reduction=$totalReduction, " +
                    "damage=$damageBefore->${e.amount}"
            }
        }
    }
}
