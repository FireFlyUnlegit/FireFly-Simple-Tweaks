package dev.firefly.simpletweaks.enchantments.handlers.uncommon

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.uncommon.EnchantEffectBonus
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.invalid
import net.minecraft.entity.EntityLivingBase
import net.minecraftforge.event.entity.living.LivingHurtEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import kotlin.random.Random.Default.nextFloat

object EnchantEffectBonusHandler : Listenable {
    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        val attacker = (e.source.trueSource as? EntityLivingBase)?: return
        val target = e.entityLiving?: return
        val lvl = getItemSpecificEnchantLevel( attacker.heldItemMainhand , EnchantEffectBonus)
        if (lvl > 0 && nextFloat() < 0.35 + 0.06 * lvl) {
            e.amount *= 0.02f * lvl * target.activePotionEffects.size + 1f
        }
    }
}