package dev.firefly.simpletweaks.enchantments.handlers.uncommon

import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.LivingEntity
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment

/**
 * 1.21 port of `enchantments/handlers/uncommon/EnchantBloodLustHandler.kt`.
 *
 * Line-by-line mapping from 1.12.2:
 * | 1.12.2                                     | 1.21.1                                            |
 * |--------------------------------------------|---------------------------------------------------|
 * | `net.minecraftforge...LivingHurtEvent`     | `compat.event.LivingHurtEvent`                     |
 * | `EntityLivingBase`                         | `LivingEntity`                                     |
 * | `source.trueSource`                        | `source.attacker` (method_5529)                    |
 * | `attacker.heldItemMainhand`                | `attacker.mainHandStack` (method_6047)             |
 * | `EnchantBloodLust` (Enchantment object)    | `GeneratedEnchantments.BLOODLUST` (RegistryKey)       |
 *
 * ⚠️ **DEVIATION — please review by hand (the only one in this file).**
 * 1.12.2 wrote the two-step form `val attacker = source.trueSource ?: return` followed by
 * `val stack = (attacker as? EntityLivingBase)?.heldItemMainhand ?: return`, and then used
 * `attacker.maxHealth` / `attacker.health` on the *un-cast* `Entity`-typed local. `DamageSource`
 * still returns `Entity` in 1.21 (`getAttacker`, method_5529) and Kotlin does not smart-cast a local
 * out of an `as?` nested in a safe-call chain, so the living-entity cast is folded into the
 * declaration here: `val attacker = (source.attacker as? LivingEntity) ?: return`, then
 * `attacker.mainHandStack`. Same filter, same order, no behaviour change — but it is a two-line merge.
 */
@ModEnchantment(
    id = "bloodlust",
    category = EnchantCategory.UNCOMMON,
    type = EnchantType.SWORD,
    maxLevel = 5,
    weight = 8,
    anvilCost = 4,
    minCostBase = 28,
    minCostPerLevel = 3,
    supportedItems = "#minecraft:enchantable/sword",
    slots = [EnchantSlot.MAINHAND],
    damagePerLevel = 0.25,
    order = 7,
)
object EnchantBloodLustHandler : Listenable {
    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        val source = e.source
        val attacker = (source.attacker as? LivingEntity)?: return
        val stack = attacker.mainHandStack
        val level = getItemSpecificEnchantLevel(stack, GeneratedEnchantments.BLOODLUST)
        if (level > 0) {
            val ratio = (attacker.maxHealth - attacker.health) / attacker.maxHealth
            val bonus = ratio * (0.005 + ((level-1) * 0.005))
            if (bonus > 0.0) {
                e.amount = (e.amount + e.amount*bonus).toFloat()
                STLog.log("BloodLust") {
                    "attacker=${attacker.name.string}, ratio=$ratio, lvl=$level, bonus=$bonus, damage=${e.amount}"
                }
            }
        }
    }
}
