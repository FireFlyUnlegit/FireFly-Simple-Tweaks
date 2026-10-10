package dev.firefly.simpletweaks.enchantments.handlers.common

import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.LivingEntity
import kotlin.random.Random
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment

/**
 * 1.21 port of `enchantments/handlers/common/EnchantAcidAttackHandler.kt`.
 *
 * This is the phase 0 vertical slice: the simplest handler in the project (25 lines, one event,
 * one enchantment lookup), chosen so the whole toolchain — mixin seam, event bus, enchantment JSON,
 * registry-key lookup — is exercised end to end before the other 54 are ported.
 *
 * Line-by-line mapping from 1.12.2:
 * | 1.12.2                                        | 1.21.1                                            |
 * |-----------------------------------------------|---------------------------------------------------|
 * | `net.minecraftforge...SubscribeEvent`         | `compat.event.SubscribeEvent`                     |
 * | `net.minecraftforge...LivingHurtEvent`        | `compat.event.LivingHurtEvent`                    |
 * | `e.source.trueSource`                         | `e.source.attacker` (Yarn method_5529)            |
 * | `attacker.heldItemMainhand`                   | `attacker.mainHandStack` (method_6047)            |
 * | `EnchantAcidAttack` (Enchantment object)      | `GeneratedEnchantments.ACID_ATTACK` (RegistryKey)    |
 * | `stack.isItemStackDamageable`                 | `stack.isDamageable` (method_7963)                |
 * | `stack.damageItem(lvl, target)`               | `stack.damage(lvl, target, EquipmentSlot.MAINHAND)` (method_7970) |
 */
@ModEnchantment(
    id = "acid_attack",
    category = EnchantCategory.COMMON,
    type = EnchantType.SWORD,
    maxLevel = 3,
    weight = 10,
    anvilCost = 2,
    minCostBase = 25,
    minCostPerLevel = 10,
    supportedItems = "#minecraft:enchantable/sword",
    slots = [EnchantSlot.MAINHAND],
    damagePerLevel = 0.2,
    order = 0,
)
object EnchantAcidAttackHandler : Listenable {

    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return

        val attacker = e.source.attacker as? LivingEntity ?: return
        val target = e.entityLiving

        val lvl = getItemSpecificEnchantLevel(attacker.mainHandStack, GeneratedEnchantments.ACID_ATTACK)
        if (lvl <= 0) return

        val rate = 0.15f * lvl + 0.1f
        val stack = target.mainHandStack

        if (Random.nextFloat() <= rate && stack.isDamageable) {
            val damageBefore = stack.damage
            stack.damage(lvl, target, EquipmentSlot.MAINHAND)
            STLog.log("AcidAttack") {
                "attacker=${attacker.name.string}, target=${target.name.string}, lvl=$lvl, rate=$rate, " +
                    "damage=$damageBefore->${stack.damage}"
            }
        }
    }
}
