package dev.firefly.simplemod.enchantments.enchantment_handlers

import dev.firefly.simplemod.core.Listenable
import dev.firefly.simplemod.enchantments.EnchantEffectBonus
import dev.firefly.simplemod.util.getItemSpecificEnchantLevel
import dev.firefly.simplemod.util.isClientSide
import net.minecraft.entity.EntityLivingBase
import net.minecraftforge.event.entity.living.LivingHurtEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import kotlin.random.Random.Default.nextFloat

object EnchantEffectBonusHandler : Listenable {
    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.isClientSide) return
        val attacker = (e.source.trueSource as? EntityLivingBase)?: return
        val target = e.entityLiving?: return
        val lvl = getItemSpecificEnchantLevel( attacker.heldItemMainhand , EnchantEffectBonus.INSTANCE)
        if (lvl > 0 && nextFloat() < 0.35 + 0.06 * lvl) {
            e.amount *= 0.02f * lvl * target.activePotionEffects.size + 1f
        }
    }
}