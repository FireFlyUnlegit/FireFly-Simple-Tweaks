package dev.firefly.simplemod.enchantments.handlers.rare

import dev.firefly.simplemod.core.Listenable
import dev.firefly.simplemod.enchantments.rare.EnchantExecute
import dev.firefly.simplemod.util.getItemSpecificEnchantLevel
import dev.firefly.simplemod.util.invalid
import net.minecraft.entity.EntityLivingBase
import net.minecraftforge.event.entity.living.LivingHurtEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object EnchantExecuteHandler : Listenable {
    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        val attacker = (e.source.trueSource as? EntityLivingBase)?: return
        val target = e.entityLiving?: return
        val lvl = getItemSpecificEnchantLevel(attacker.heldItemMainhand, EnchantExecute)
        if (lvl > 0) {
            val lostHealth = ((target.maxHealth + target.absorptionAmount) - (target.health+ target.absorptionAmount)) * 0.02f * lvl
            e.amount += lostHealth
        }
    }
}