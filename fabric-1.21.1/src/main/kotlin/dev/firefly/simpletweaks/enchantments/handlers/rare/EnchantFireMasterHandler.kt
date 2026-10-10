package dev.firefly.simpletweaks.enchantments.handlers.rare

import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.compat.attacker
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.compat.target
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.util.fireTime
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.player.PlayerEntity
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment

/**
 * 1.21 port of `enchantments/handlers/rare/EnchantFireMasterHandler.kt`.
 *
 * Line-by-line mapping from 1.12.2:
 * | 1.12.2                                                   | 1.21.1                                                          |
 * |----------------------------------------------------------|-----------------------------------------------------------------|
 * | `net.minecraftforge...LivingHurtEvent`                   | `compat.event.LivingHurtEvent`                                  |
 * | `EntityPlayer`                                           | `net.minecraft.entity.player.PlayerEntity`                      |
 * | `attacker.heldItemMainhand`                              | `attacker.mainHandStack` (method_6047)                          |
 * | `target.fireTime` (mixin accessor in 1.12.2)             | `util/EntityUtil.kt`'s `fireTime` extension (`getFireTicks`/`setFireTicks`, now public) |
 * | `MobEffects.FIRE_RESISTANCE`                             | `net.minecraft.entity.effect.StatusEffects.FIRE_RESISTANCE` (a `RegistryEntry`) |
 * | `PotionEffect(effect, duration, amplifier)`              | `StatusEffectInstance(effect, duration, amplifier)`             |
 * | `attacker.addPotionEffect(...)`                          | `attacker.addStatusEffect(...)` (method_6093 family)            |
 * | `attacker.extinguish()`                                  | unchanged (`Entity.extinguish`, method_5728)                    |
 * | `EnchantFireMaster` (Enchantment object)                 | `GeneratedEnchantments.FIRE_MASTER` (RegistryKey)                  |
 */
@ModEnchantment(
    id = "fire_master",
    category = EnchantCategory.RARE,
    type = EnchantType.SWORD,
    maxLevel = 6,
    weight = 6,
    anvilCost = 6,
    minCostBase = 24,
    minCostPerLevel = 4,
    supportedItems = "#minecraft:enchantable/sword",
    slots = [EnchantSlot.MAINHAND],
    damagePerLevel = 0.25,
    order = 19,
)
object EnchantFireMasterHandler : Listenable {

    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        val attacker = e.attacker as? PlayerEntity ?: return
        val target = e.target
        val lvl = getItemSpecificEnchantLevel(attacker.mainHandStack, GeneratedEnchantments.FIRE_MASTER)
        if (lvl > 0) {
            val fireTimeBefore = target.fireTime
            val fireBonus = (target.fireTime * 0.05f).coerceAtLeast(0f)
            e.amount += fireBonus
            target.fireTime += lvl * 10
            attacker.addStatusEffect(StatusEffectInstance(StatusEffects.FIRE_RESISTANCE,lvl * 20,0))
            attacker.extinguish()
            STLog.log("FireMaster") {
                "attacker=${attacker.name.string}, target=${target.name.string}, lvl=$lvl, " +
                    "targetFireTime=$fireTimeBefore->${target.fireTime}, fireBonus=$fireBonus, damage=${e.amount}, " +
                    "selfFireResistanceTicks=${lvl * 20}"
            }
        }
    }
}
