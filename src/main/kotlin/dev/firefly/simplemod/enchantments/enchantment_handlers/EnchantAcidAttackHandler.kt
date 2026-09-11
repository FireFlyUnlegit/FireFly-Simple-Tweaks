package dev.firefly.simplemod.enchantments.enchantment_handlers

import dev.firefly.simplemod.core.Listenable
import dev.firefly.simplemod.enchantments.EnchantAcidAttack
import dev.firefly.simplemod.util.getItemSpecificEnchantLevel
import dev.firefly.simplemod.util.isClientSide
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraftforge.event.entity.living.LivingHurtEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object EnchantAcidAttackHandler : Listenable {
    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.isClientSide) return
        val attacker = (e.source.trueSource as? EntityLivingBase)?: return
        val target = e.entityLiving?: return
        val lvl = getItemSpecificEnchantLevel( attacker.heldItemMainhand , EnchantAcidAttack.INSTANCE)
        val rate = 0.15 * lvl + 0.1
        val stack = target.heldItemMainhand
        if (lvl > 0 && kotlin.random.Random.nextFloat() <= rate && stack.isItemStackDamageable) {
            stack.damageItem(lvl,target)
        }
    }
}