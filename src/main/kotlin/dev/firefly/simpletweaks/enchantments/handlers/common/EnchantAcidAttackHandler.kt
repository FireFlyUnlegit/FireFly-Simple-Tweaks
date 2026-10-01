package dev.firefly.simpletweaks.enchantments.handlers.common

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.common.EnchantAcidAttack
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.invalid
import net.minecraft.entity.EntityLivingBase
import net.minecraftforge.event.entity.living.LivingHurtEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import kotlin.random.Random

object EnchantAcidAttackHandler : Listenable {
    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        val attacker = (e.source.trueSource as? EntityLivingBase)?: return
        val target = e.entityLiving?: return
        val lvl = getItemSpecificEnchantLevel( attacker.heldItemMainhand , EnchantAcidAttack)
        val rate = 0.15 * lvl + 0.1
        val stack = target.heldItemMainhand
        if (lvl > 0 && Random.nextFloat() <= rate && stack.isItemStackDamageable) {
            stack.damageItem(lvl,target)
        }
    }
}