package dev.firefly.simpletweaks.enchantments.handlers.common

import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.compat.attacker
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.relativeSpeed
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment

/**
 * 1.21 port of `enchantments/handlers/common/EnchantMotionBonusHandler.kt`.
 *
 * Line-by-line mapping from 1.12.2:
 * | 1.12.2                                  | 1.21.1                                                             |
 * |-----------------------------------------|--------------------------------------------------------------------|
 * | `net.minecraftforge...LivingHurtEvent`  | `compat.event.LivingHurtEvent`                                      |
 * | `e.attacker`                            | `compat.attacker` extension (Forge's `source.trueSource`, method_5529) |
 * | `e.entity` (LivingEvent#getEntity)      | `e.entityLiving` (the port event's own field)                       |
 * | `attacker.heldItemMainhand`             | `attacker.mainHandStack` (method_6047)                              |
 * | `EnchantMotionBonus` (Enchantment)      | `GeneratedEnchantments.MOTION_BONUS` (RegistryKey)                     |
 *
 * `relativeSpeed(...)` is the `util/EntityUtil.kt` extension — see the batch report: its 1.21.1 port
 * does not exist yet (`motionX/Y/Z` → `Entity.getVelocity()`, method_18799 is a setter only), so the
 * call is kept 1:1 and will resolve once that util file lands.
 */
@ModEnchantment(
    id = "motion_bonus",
    category = EnchantCategory.COMMON,
    type = EnchantType.SWORD,
    maxLevel = 5,
    weight = 10,
    anvilCost = 2,
    minCostBase = 19,
    minCostPerLevel = 4,
    supportedItems = "#minecraft:enchantable/sword",
    slots = [EnchantSlot.MAINHAND],
    damagePerLevel = 0.2,
    order = 4,
)
object EnchantMotionBonusHandler : Listenable {
    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        val attacker = e.attacker?: return
        val lvl = getItemSpecificEnchantLevel(attacker.mainHandStack, GeneratedEnchantments.MOTION_BONUS)
        if (lvl > 0) {
            val speed = relativeSpeed(attacker,e.entityLiving).toFloat()
            val bonusBefore = (speed * lvl * 0.2f).coerceAtMost(lvl.toFloat())
            e.amount *= bonusBefore + 1f
            STLog.log("MotionBonus") {
                "attacker=${attacker.name.string}, target=${e.entityLiving.name.string}, lvl=$lvl, " +
                    "speed=$speed, bonus=$bonusBefore, damage=${e.amount}"
            }
        }
    }
}
