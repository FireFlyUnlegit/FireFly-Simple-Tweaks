package dev.firefly.simpletweaks.enchantments.handlers.rare

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
 * 1.21 port of `enchantments/handlers/rare/EnchantExecuteHandler.kt`.
 *
 * Line-by-line mapping from 1.12.2:
 * | 1.12.2                                        | 1.21.1                                                          |
 * |-----------------------------------------------|-----------------------------------------------------------------|
 * | `net.minecraftforge...LivingHurtEvent`        | `compat.event.LivingHurtEvent`                                  |
 * | `EntityLivingBase`                            | `net.minecraft.entity.LivingEntity`                             |
 * | `e.source.trueSource`                         | `e.source.attacker` (Yarn `DamageSource.getAttacker`, method_5529) |
 * | `attacker.heldItemMainhand`                   | `attacker.mainHandStack` (method_6047)                          |
 * | `target.maxHealth` / `target.health` / `target.absorptionAmount` | unchanged (method_6063 / method_6032 / method_6067) |
 * | `EnchantExecute` (Enchantment object)         | `GeneratedEnchantments.EXECUTE` (RegistryKey)                      |
 */
@ModEnchantment(
    id = "execute",
    category = EnchantCategory.RARE,
    type = EnchantType.SWORD,
    maxLevel = 5,
    weight = 6,
    anvilCost = 6,
    minCostBase = 25,
    minCostPerLevel = 5,
    supportedItems = "#minecraft:enchantable/sword",
    slots = [EnchantSlot.MAINHAND],
    damagePerLevel = 0.3,
    order = 16,
)
object EnchantExecuteHandler : Listenable {
    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        val attacker = (e.source.attacker as? LivingEntity)?: return
        val target = e.entityLiving
        val lvl = getItemSpecificEnchantLevel(attacker.mainHandStack, GeneratedEnchantments.EXECUTE)
        if (lvl > 0) {
            val lostHealth = ((target.maxHealth + target.absorptionAmount) - (target.health+ target.absorptionAmount)) * 0.02f * lvl
            val damageBefore = e.amount
            e.amount += lostHealth
            STLog.log("Execute") {
                "attacker=${attacker.name.string}, target=${target.name.string}, lvl=$lvl, " +
                    "health=${target.health}/${target.maxHealth}, lostHealth=$lostHealth, " +
                    "damage=$damageBefore->${e.amount}"
            }
        }
    }
}
