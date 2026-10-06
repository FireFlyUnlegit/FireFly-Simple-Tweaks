package dev.firefly.simpletweaks.enchantments.handlers.rare

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
 * 1.21 port of `enchantments/handlers/rare/EnchantAssassinHandler.kt`.
 *
 * Line-by-line mapping from 1.12.2:
 * | 1.12.2                                        | 1.21.1                                                          |
 * |-----------------------------------------------|-----------------------------------------------------------------|
 * | `net.minecraftforge...LivingDamageEvent`      | `compat.event.LivingDamageEvent`                                |
 * | `net.minecraftforge...EventPriority`          | `compat.event.EventPriority` (annotation keeps `priority = LOWEST`) |
 * | `EntityLivingBase`                            | `net.minecraft.entity.LivingEntity`                             |
 * | `e.source.trueSource`                         | `e.source.attacker` (Yarn `DamageSource.getAttacker`, method_5529) |
 * | `attacker.heldItemMainhand`                   | `attacker.mainHandStack` (method_6047)                          |
 * | `target.maxHealth` / `target.absorptionAmount`| unchanged (`getMaxHealth` method_6063 / `getAbsorptionAmount` method_6067) |
 * | `EnchantAssassin` (Enchantment object)        | `ModEnchantmentKeys.ASSASSIN` (RegistryKey)                     |
 */
object EnchantAssassinHandler : Listenable {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onLivingHurt(e: LivingDamageEvent) {
        if (e.invalid) return
        val attacker = (e.source.attacker as? LivingEntity)?: return
        val target = e.entityLiving?: return
        val lvl = getItemSpecificEnchantLevel( attacker.mainHandStack , ModEnchantmentKeys.ASSASSIN)
        if (lvl > 0) {
            val threshold = target.maxHealth * 0.01f * lvl
            val remaining = target.health - e.amount
            if (remaining < threshold) {
                val damageBefore = e.amount
                e.amount = target.maxHealth + target.absorptionAmount
                STLog.log("Assassin") {
                    "attacker=${attacker.name.string}, target=${target.name.string}, lvl=$lvl, " +
                        "health=${target.health}, threshold=$threshold, remaining=$remaining, " +
                        "damage=$damageBefore->${e.amount}, outcome=execute"
                }
            }
        }
    }
}
