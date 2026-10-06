package dev.firefly.simpletweaks.enchantments.handlers.legendary

import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.compat.event.EventPriority
import dev.firefly.simpletweaks.compat.event.LivingDamageEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.ModEnchantmentKeys
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.EquipmentSlot
import kotlin.math.max

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
 * | `EnchantDamageLimiter` (Enchantment object)                  | `ModEnchantmentKeys.DAMAGE_LIMITER` (RegistryKey)                |
 *
 * No forced mappings: the clamp formula, the `in limit..maxLimit` window, the LOW priority and the
 * max-over-armour-slots scan are all copied verbatim.
 */
object EnchantDamageLimiterHandler : Listenable {

    @SubscribeEvent(priority = EventPriority.LOW)
    fun onLivingDamage(e: LivingDamageEvent) {
        if (e.invalid) return
        val p = e.entityLiving ?: return

        var lvl = 0
        for (slot in EquipmentSlot.entries) {
            if (slot.type != EquipmentSlot.Type.HUMANOID_ARMOR) continue
            lvl = max(lvl, getItemSpecificEnchantLevel(p.getEquippedStack(slot), ModEnchantmentKeys.DAMAGE_LIMITER))
        }
        if (lvl <= 0) return

        val maxHealth = p.maxHealth
        val limit = (0.8f - 0.2f * lvl) * maxHealth
        val maxLimit = lvl * maxHealth

        if (e.amount in limit..maxLimit) {
            val damageBefore = e.amount
            e.amount = limit
            STLog.log("DamageLimiter") {
                "player=${p.name.string}, lvl=$lvl, maxHealth=$maxHealth, limit=$limit, maxLimit=$maxLimit, " +
                    "damage=$damageBefore->${e.amount}, outcome=clamped"
            }
        }
    }
}
