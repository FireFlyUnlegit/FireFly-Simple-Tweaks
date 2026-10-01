package dev.firefly.simpletweaks.enchantments.handlers.uncommon

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.uncommon.EnchantSuperKnockback
import dev.firefly.simpletweaks.util.attacker
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.invalid
import dev.firefly.simpletweaks.util.target
import net.minecraft.util.math.Vec3d
import net.minecraftforge.event.entity.living.LivingHurtEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import kotlin.random.Random

object EnchantSuperKnockbackHandler : Listenable {
    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        val attacker =e.attacker ?: return
        val target = e.target
        val lvl = getItemSpecificEnchantLevel(
            itemStack = attacker.heldItemMainhand,
            enchantment = EnchantSuperKnockback
        )
        if (lvl>0) {
            val lookVec =
                target.positionVector.subtract(attacker.positionVector)
            val horizontalVec = Vec3d(lookVec.x, 0.0, lookVec.z).normalize()
            val knockbackStrength = 2F + (lvl - 1) * 0.33f
            if (target.isRiding) target.dismountRidingEntity()
            target.motionX = horizontalVec.x * knockbackStrength
            target.motionY = Random.Default.nextDouble(0.4, 1.0)
            target.motionZ = horizontalVec.z * knockbackStrength
            target.velocityChanged = true
        }
    }
}