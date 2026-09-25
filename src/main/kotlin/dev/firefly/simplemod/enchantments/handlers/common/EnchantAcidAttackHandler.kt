package dev.firefly.simplemod.enchantments.handlers.common

import dev.firefly.simplemod.core.Listenable
import dev.firefly.simplemod.enchantments.common.EnchantAcidAttack
import dev.firefly.simplemod.util.getItemSpecificEnchantLevel
import dev.firefly.simplemod.util.invalid
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