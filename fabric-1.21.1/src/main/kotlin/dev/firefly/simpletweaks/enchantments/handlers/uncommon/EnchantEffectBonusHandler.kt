package dev.firefly.simpletweaks.enchantments.handlers.uncommon

import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.LivingEntity
import kotlin.random.Random.Default.nextFloat
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment

/**
 * 1.21 port of `enchantments/handlers/uncommon/EnchantEffectBonusHandler.kt`.
 *
 * Line-by-line mapping from 1.12.2:
 * | 1.12.2                                        | 1.21.1                                                       |
 * |-----------------------------------------------|--------------------------------------------------------------|
 * | `net.minecraftforge...LivingHurtEvent`        | `compat.event.LivingHurtEvent`                                |
 * | `EntityLivingBase`                            | `LivingEntity`                                                |
 * | `e.source.trueSource`                         | `e.source.attacker` (Yarn `DamageSource.getAttacker`, method_5529) |
 * | `attacker.heldItemMainhand`                   | `attacker.mainHandStack` (method_6047)                        |
 * | `target.activePotionEffects.size`             | `target.statusEffects.size` (`LivingEntity.getStatusEffects()`, method_6026, returns `Collection<StatusEffectInstance>`) |
 * | `EnchantEffectBonus` (Enchantment object)     | `GeneratedEnchantments.EFFECT_BONUS` (RegistryKey)               |
 */
@ModEnchantment(
    id = "effect_bonus",
    category = EnchantCategory.UNCOMMON,
    type = EnchantType.SWORD,
    maxLevel = 3,
    weight = 8,
    anvilCost = 4,
    minCostBase = 22,
    minCostPerLevel = 6,
    supportedItems = "#minecraft:enchantable/sword",
    slots = [EnchantSlot.MAINHAND],
    damagePerLevel = 0.25,
    order = 8,
)
object EnchantEffectBonusHandler : Listenable {
    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        val attacker = (e.source.attacker as? LivingEntity)?: return
        val target = e.entityLiving?: return
        val lvl = getItemSpecificEnchantLevel( attacker.mainHandStack , GeneratedEnchantments.EFFECT_BONUS)
        val roll = nextFloat()
        if (lvl > 0 && roll < 0.35 + 0.06 * lvl) {
            e.amount *= 0.02f * lvl * target.statusEffects.size + 1f
            STLog.log("EffectBonus") {
                "attacker=${attacker.name.string}, target=${target.name.string}, lvl=$lvl, " +
                    "roll=$roll, threshold=${0.35 + 0.06 * lvl}, effects=${target.statusEffects.size}, damage=${e.amount}"
            }
        }
    }
}
