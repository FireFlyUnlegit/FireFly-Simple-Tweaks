package dev.firefly.simpletweaks.enchantments.handlers.common

import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.compat.attacker
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.ModEnchantmentKeys
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.relativeSpeed

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
 * | `EnchantMotionBonus` (Enchantment)      | `ModEnchantmentKeys.MOTION_BONUS` (RegistryKey)                     |
 *
 * `relativeSpeed(...)` is the `util/EntityUtil.kt` extension — see the batch report: its 1.21.1 port
 * does not exist yet (`motionX/Y/Z` → `Entity.getVelocity()`, method_18799 is a setter only), so the
 * call is kept 1:1 and will resolve once that util file lands.
 */
object EnchantMotionBonusHandler : Listenable {
    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        val attacker = e.attacker?: return
        val lvl = getItemSpecificEnchantLevel(attacker.mainHandStack, ModEnchantmentKeys.MOTION_BONUS)
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
