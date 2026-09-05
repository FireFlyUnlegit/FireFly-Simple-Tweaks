package dev.firefly.simplemod.enchantments.enchantment_handlers

import dev.firefly.simplemod.core.Listenable
import dev.firefly.simplemod.enchantments.EnchantExecute
import dev.firefly.simplemod.util.getItemSpecificEnchantLevel
import net.minecraft.entity.EntityLiving
import net.minecraft.entity.EntityLivingBase
import net.minecraftforge.event.entity.living.LivingHurtEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object EnchantExecuteHandler : Listenable {
    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.entity.world.isRemote) return
        val attacker = (e.source.trueSource as? EntityLivingBase)?: return
        val target = e.entityLiving?: return
        val lvl = getItemSpecificEnchantLevel(attacker.heldItemMainhand, EnchantExecute.INSTANCE)
        if (lvl > 0) {
            val lostHealth = (target.maxHealth - target.health) * 0.02f * lvl
            e.amount += lostHealth
        }
    }
}