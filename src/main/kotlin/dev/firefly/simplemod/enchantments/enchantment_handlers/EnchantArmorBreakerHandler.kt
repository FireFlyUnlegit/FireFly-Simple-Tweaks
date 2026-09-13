package dev.firefly.simplemod.enchantments.enchantment_handlers

import dev.firefly.simplemod.core.Listenable
import dev.firefly.simplemod.enchantments.EnchantArmorBreaker
import dev.firefly.simplemod.util.getItemSpecificEnchantLevel
import dev.firefly.simplemod.util.getRandomArmor
import dev.firefly.simplemod.util.invalid
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraftforge.event.entity.living.LivingHurtEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import kotlin.random.Random.Default.nextFloat

object EnchantArmorBreakerHandler : Listenable {
    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        val attacker = (e.source.trueSource as? EntityLivingBase)?: return
        val target = e.entityLiving as? EntityPlayer?: return

        val lvl = getItemSpecificEnchantLevel( attacker.heldItemMainhand, EnchantArmorBreaker.INSTANCE)
        val rate = 0.15 * lvl + 0.1
        if (lvl > 0 && rate > nextFloat()) {
            target.getRandomArmor().damageItem(lvl,target)
        }
    }
}