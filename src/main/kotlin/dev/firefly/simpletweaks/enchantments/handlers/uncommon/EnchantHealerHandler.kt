package dev.firefly.simpletweaks.enchantments.handlers.uncommon

import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.compat.event.EventPriority
import dev.firefly.simpletweaks.compat.event.LivingDamageEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.ModEnchantmentKeys
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.LivingEntity

/**
 * 1.21 port of `enchantments/handlers/uncommon/EnchantHealerHandler.kt`.
 *
 * Line-by-line mapping from 1.12.2:
 * | 1.12.2                                    | 1.21.1                                                       |
 * |-------------------------------------------|--------------------------------------------------------------|
 * | `net.minecraftforge...LivingDamageEvent`  | `compat.event.LivingDamageEvent`                              |
 * | `net.minecraftforge...EventPriority`      | `compat.event.EventPriority` (annotation keeps `priority = LOW`) |
 * | `EntityLivingBase`                        | `LivingEntity`                                                |
 * | `e.source.trueSource`                     | `e.source.attacker` (Yarn `DamageSource.getAttacker`, method_5529) |
 * | `attacker.heldItemMainhand`               | `attacker.mainHandStack` (method_6047)                        |
 * | `living.health = x`                       | `living.health = x` (`LivingEntity.setHealth(float)`, method_6033) |
 * | `EnchantHealer` (Enchantment object)      | `ModEnchantmentKeys.HEALER` (RegistryKey)                     |
 */
object EnchantHealerHandler : Listenable{
    @SubscribeEvent(priority = EventPriority.LOW)
    fun onLivingHurt(e: LivingDamageEvent) {
        if (e.invalid) return
        val lvl = getItemSpecificEnchantLevel((((e.source.attacker as? LivingEntity) ?:return)
            .mainHandStack),
            ModEnchantmentKeys.HEALER)
        if (lvl > 0)
        {
            val originDMG = e.amount
            val living = e.entityLiving
            val healthBefore = living.health
            val healingFactor = 0.2f * lvl
            living.health = (originDMG * healingFactor + e.entityLiving.health).coerceAtMost(living.maxHealth)
            e.isCanceled = true
            STLog.log("Healer") {
                "attacker=${e.source.attacker?.name?.string}, healer=${living.name.string}, lvl=$lvl, " +
                    "damage=$originDMG, factor=$healingFactor, health=$healthBefore->${living.health}, " +
                    "outcome=damage-cancelled"
            }
        }
    }
}

