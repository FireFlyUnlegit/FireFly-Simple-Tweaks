package dev.firefly.simplemod.enchantments.enchantment_handlers.infinitepower

import dev.firefly.simplemod.enchantments.EnchantInfinitePower
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.entity.player.EntityPlayer
import net.minecraftforge.event.entity.living.LivingAttackEvent
import net.minecraftforge.event.entity.living.LivingDamageEvent
import net.minecraftforge.event.entity.living.LivingDeathEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object ForgeHandler {
    @SubscribeEvent
    fun onLivingAttack(event: LivingAttackEvent) {
        val entity = event.entity
        if (entity !is EntityPlayer) return
        val player = entity
        val stack = player.heldItemMainhand
        val level = EnchantmentHelper.getEnchantmentLevel(EnchantInfinitePower.INSTANCE, stack)
        if (level <= 0) return
        event.isCanceled = true
        if (player.health < player.maxHealth) {
            player.health = player.maxHealth
        }
    }

    @SubscribeEvent
    fun onLivingDeath(event: LivingDeathEvent) {
        val entity = event.entity
        if (entity !is EntityPlayer) return
        val player = entity
        val stack = player.heldItemMainhand
        val level = EnchantmentHelper.getEnchantmentLevel(EnchantInfinitePower.INSTANCE, stack)
        if (level <= 0) return
        event.isCanceled = true
        player.health = player.maxHealth
        player.deathTime = 0
        player.isDead = false
    }

    @SubscribeEvent
    fun onLivingDamage(e: LivingDamageEvent) {
        val entity = e.entity
        if (entity !is EntityPlayer) return
        val player = entity
        val stack = player.heldItemMainhand
        val level = EnchantmentHelper.getEnchantmentLevel(EnchantInfinitePower.INSTANCE, stack)
        if (level <= 0) return
        e.isCanceled = true
        e.amount = 0.0f
    }
}