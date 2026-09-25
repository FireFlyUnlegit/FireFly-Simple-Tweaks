package dev.firefly.simplemod.enchantments.handlers.mythic.infinitepower

import dev.firefly.simplemod.enchantments.handlers.mythic.EnchantInfinitePowerHandler
import dev.firefly.simplemod.enchantments.mythic.EnchantInfinitePower
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.entity.SharedMonsterAttributes
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.init.MobEffects
import net.minecraft.potion.PotionEffect
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.PlayerEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.util.*

object FlightHandler {
    private val isControllingFlight = mutableMapOf<UUID, Boolean>()

    @SubscribeEvent
    fun onPlayerTick(event: TickEvent.PlayerTickEvent) {
        if (event.phase != TickEvent.Phase.END) return
        val player = event.player
        val stack = player.heldItemMainhand
        val level = EnchantmentHelper.getEnchantmentLevel(EnchantInfinitePower, stack)
        val uuid=player.uniqueID
        if (level > 0) {
            if (player.health < player.maxHealth) {
                player.health = player.maxHealth
            }
            player.capabilities.allowFlying = true
            isControllingFlight[uuid] = true
            if (player is EntityPlayerMP) {
                player.sendPlayerAbilities()
            }
            if (!stack.isEmpty) {
                stack.itemDamage = 0
            }
            player.addPotionEffect(PotionEffect(MobEffects.NIGHT_VISION, 400, 0, true, false))
            player.foodStats.foodLevel = 20
            player.foodStats.setFoodSaturationLevel(20f)
            val attr = player.getEntityAttribute(SharedMonsterAttributes.ATTACK_SPEED)
            if (attr != null && attr.getModifier(EnchantInfinitePowerHandler.ATTACK_SPEED_MODIFIER.id) == null) {
                attr.applyModifier(EnchantInfinitePowerHandler.ATTACK_SPEED_MODIFIER)
            }
        } else {
            if (!player.capabilities.isCreativeMode && !player.isSpectator && isControllingFlight[uuid]==true) {
                isControllingFlight[uuid] = false
                player.capabilities.allowFlying = false
                player.capabilities.isFlying = false
                if (player is EntityPlayerMP) {
                    player.sendPlayerAbilities()
                }
            }
            if (player.isPotionActive(MobEffects.NIGHT_VISION) && player.getActivePotionEffect(MobEffects.NIGHT_VISION)?.isAmbient == true) {
                player.removePotionEffect(MobEffects.NIGHT_VISION)
            }
            val attr = player.getEntityAttribute(SharedMonsterAttributes.ATTACK_SPEED)
            attr.removeModifier(EnchantInfinitePowerHandler.ATTACK_SPEED_MODIFIER)
        }
    }
    @SubscribeEvent
    fun onPlayerLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        isControllingFlight.remove(event.player.uniqueID)
    }
}