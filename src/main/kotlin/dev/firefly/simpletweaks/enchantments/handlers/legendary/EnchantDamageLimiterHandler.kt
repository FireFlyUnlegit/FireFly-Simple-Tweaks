package dev.firefly.simpletweaks.enchantments.handlers.legendary

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.event.EventPriority
import dev.firefly.simpletweaks.compat.event.LivingDamageEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.EquipmentSlot
import kotlin.math.max
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantItemWeights
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSupportedItemTags
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment
import dev.firefly.simpletweaks.util.getArmorEnchantLevel

/**
 * 1.21 port of `enchantments/handlers/legendary/EnchantDamageLimiterHandler.kt`.
 *
 * Line-by-line mapping from 1.12.2:
 * | 1.12.2                                                       | 1.21.1                                                          |
 * |--------------------------------------------------------------|-----------------------------------------------------------------|
 * | `net.minecraftforge...LivingDamageEvent`                     | `compat.event.LivingDamageEvent`                                 |
 * | `net.minecraftforge...EventPriority`                         | `compat.event.EventPriority` (the annotation keeps `priority`)   |
 * | `EntityEquipmentSlot.entries` + `slotType != Type.ARMOR`     | `EquipmentSlot.entries` + `slot.type != EquipmentSlot.Type.HUMANOID_ARMOR` (1.21 renamed the slot-type constant; `ANIMAL_ARMOR` is the new non-humanoid one — same shape already used by `EnchantVitalityHandler`) |
 * | `p.getItemStackFromSlot(slot)`                               | `p.getEquippedStack(slot)` (`method_6118`)                       |
 * | `p.maxHealth`                                                | `p.maxHealth` (`getMaxHealth()`, `method_6063`)                  |
 * | `e.amount` (read + write)                                    | `e.amount` (mutable `Float`, same as Forge)                      |
 * | `EnchantDamageLimiter` (Enchantment object)                  | `GeneratedEnchantments.DAMAGE_LIMITER` (RegistryKey)                |
 *
 * No forced mappings: the clamp formula, the `in limit..maxLimit` window, the LOW priority and the
 * max-over-armour-slots scan are all copied verbatim.
 */
@ModEnchantment(
    id = "damage_limiter",
    category = EnchantCategory.LEGENDARY,
    type = EnchantType.ARMOR,
    maxLevel = 3,
    weight = EnchantItemWeights.LEGENDARY,
    anvilCost = 10,
    minCostBase = 30,
    minCostPerLevel = 8,
    supportedItems = EnchantSupportedItemTags.ARMOR,
    slots = [EnchantSlot.ARMOR],
    order = 34,
)
object EnchantDamageLimiterHandler : Listenable {

    @SubscribeEvent(priority = EventPriority.LOW)
    fun onLivingDamage(e: LivingDamageEvent) {
        if (e.invalid) return
        val p = e.entityLiving

        val lvl = p.getArmorEnchantLevel(GeneratedEnchantments.DAMAGE_LIMITER)
        if (lvl <= 0) return

        val maxHealth = p.maxHealth
        val limit = (1f - 0.075f * lvl) * maxHealth
        val maxLimit = maxHealth + maxHealth * lvl * 0.125f

        if (e.amount in limit..maxLimit) {
            val damageBefore = e.amount
            e.amount = limit
            FSTLog.log("DamageLimiter") {
                "player=${p.name.string}, lvl=$lvl, maxHealth=$maxHealth, limit=$limit, maxLimit=$maxLimit, " +
                    "damage=$damageBefore->${e.amount}, outcome=clamped"
            }
        }
    }
}
