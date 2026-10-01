package dev.firefly.simpletweaks.enchantments.handlers.rare

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.rare.EnchantFireMaster
import dev.firefly.simpletweaks.util.*
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.MobEffects
import net.minecraft.potion.PotionEffect
import net.minecraftforge.event.entity.living.LivingHurtEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object EnchantFireMasterHandler : Listenable {

    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        val attacker = e.attacker as? EntityPlayer ?: return
        val target = e.target
        val lvl = getItemSpecificEnchantLevel(attacker.heldItemMainhand, EnchantFireMaster)
        if (lvl > 0) {
            e.amount += (target.fireTime * 0.05f).coerceAtLeast(0f)
            target.fireTime += lvl * 10
            attacker.addPotionEffect(PotionEffect(MobEffects.FIRE_RESISTANCE,lvl * 20,0))
            attacker.extinguish()
        }
    }
}