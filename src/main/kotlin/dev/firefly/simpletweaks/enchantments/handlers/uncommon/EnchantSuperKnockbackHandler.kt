package dev.firefly.simpletweaks.enchantments.handlers.uncommon

import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.compat.attacker
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.compat.target
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.ModEnchantmentKeys
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.util.math.Vec3d
import kotlin.random.Random

/**
 * 1.21 port of `enchantments/handlers/uncommon/EnchantSuperKnockbackHandler.kt`.
 *
 * Line-by-line mapping from 1.12.2:
 * | 1.12.2                                        | 1.21.1                                                            |
 * |-----------------------------------------------|-------------------------------------------------------------------|
 * | `net.minecraftforge...LivingHurtEvent`        | `compat.event.LivingHurtEvent`                                     |
 * | `e.attacker` / `e.target`                     | `compat.attacker` / `compat.target` extensions (same names)        |
 * | `attacker.heldItemMainhand`                   | `attacker.mainHandStack` (method_6047)                             |
 * | `target.positionVector` / `attacker.positionVector` | `target.pos` / `attacker.pos` (`Entity.getPos()`)            |
 * | `EnchantSuperKnockback` (Enchantment object)  | `ModEnchantmentKeys.SUPER_KNOCKBACK` (RegistryKey)                 |
 * | `target.isRiding`                             | `target.hasVehicle()` (`Entity.hasVehicle()`, method_5765 — no `is` prefix, so it stays a call) |
 * | `target.dismountRidingEntity()`               | `target.stopRiding()` (method_5848)                                |
 * | `target.motionX/Y/Z = ...`                    | one `target.setVelocity(x, y, z)` (method_18800 — 1.21 has no `motion*` fields) |
 * | `target.velocityChanged = true`               | `target.velocityModified = true` (public field `field_6037`)        |
 *
 * ⚠️ **DEVIATION — please review by hand (the only one in this file).**
 * `getItemSpecificEnchantLevel(itemStack = ..., enchantment = ...)` uses named arguments in 1.12.2;
 * the port's `util/EnchantmentsUtil.kt` names the second parameter `key`, so the argument label here
 * is `key = ...`. The call itself, the order and the values are unchanged.
 */
object EnchantSuperKnockbackHandler : Listenable {
    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        val attacker =e.attacker ?: return
        val target = e.target
        val lvl = getItemSpecificEnchantLevel(
            itemStack = attacker.mainHandStack,
            key = ModEnchantmentKeys.SUPER_KNOCKBACK
        )
        if (lvl>0) {
            val lookVec =
                target.pos.subtract(attacker.pos)
            val horizontalVec = Vec3d(lookVec.x, 0.0, lookVec.z).normalize()
            val knockbackStrength = 2F + (lvl - 1) * 0.33f
            // `hasVehicle()` has no `get`/`is` prefix, so Kotlin cannot see it as a property: it stays a call.
            if (target.hasVehicle()) target.stopRiding()
            target.setVelocity(
                horizontalVec.x * knockbackStrength,
                Random.Default.nextDouble(0.4, 1.0),
                horizontalVec.z * knockbackStrength
            )
            target.velocityModified = true
            STLog.log("SuperKnockback") {
                "attacker=${attacker.name.string}, target=${target.name.string}, lvl=$lvl, " +
                    "strength=$knockbackStrength, dir=(${horizontalVec.x}, ${horizontalVec.z}), velocity=${target.velocity}"
            }
        }
    }
}
