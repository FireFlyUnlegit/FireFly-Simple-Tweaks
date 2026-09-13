package dev.firefly.simplemod.core

import dev.firefly.simplemod.SimpleMod
import dev.firefly.simplemod.enchantments.*
import dev.firefly.simplemod.enchantments.enchantment_handlers.*
import net.minecraft.enchantment.Enchantment
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.RegistryEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object EnchantmentManager {

    private val enchantments = mutableListOf<Enchantment>()

    private val enchantmentList = listOf(
        EnchantInfinitePower.INSTANCE,
        EnchantDoubleStrike.INSTANCE,
        EnchantFlight.INSTANCE,
        EnchantItemFixer.INSTANCE,
        EnchantBloodLust.INSTANCE,
        EnchantVoidProtection.INSTANCE,
        EnchantHealingBlade.INSTANCE,
        EnchantCrit.INSTANCE,
        EnchantCritDamage.INSTANCE,
        EnchantHealer.INSTANCE,
        EnchantExecute.INSTANCE,
        EnchantDoubleCrit.INSTANCE,
        EnchantEffectBonus.INSTANCE,
        EnchantImmortal.INSTANCE,
        EnchantSaturation.INSTANCE,
        EnchantAcidAttack.INSTANCE,
        EnchantArmorBreaker.INSTANCE,
        EnchantAssassin.INSTANCE,
        EnchantDamageLimiter.INSTANCE,
        EnchantAoeAttack.INSTANCE,
        EnchantChargedStrike.INSTANCE,
    )

    private val handlerList = listOf(
        EnchantInfinitePowerHandler,
        EnchantDoubleStrikeHandler,
        EnchantFlightHandler,
        EnchantItemFixerHandler,
        EnchantBloodLustHandler,
        EnchantVoidProtectionHandler,
        EnchantHealingBladeHandler,
        EnchantCritHandler,
        EnchantCritDamageHandler,
        EnchantHealerHandler,
        EnchantExecuteHandler,
        EnchantDoubleCritHandler,
        EnchantEffectBonusHandler,
        EnchantImmortalHandler,
        EnchantSaturationHandler,
        EnchantAcidAttackHandler,
        EnchantArmorBreakerHandler,
        EnchantAssassinHandler,
        EnchantDamageLimiterHandler,
        EnchantAoeAttackHandler,
        EnchantChargedStrikeHandler,
    )

    @SubscribeEvent
    fun registerEnchantments(event: RegistryEvent.Register<Enchantment>) {
        enchantmentList.forEach { enchantment ->
            enchantments.add(enchantment)
            event.registry.register(enchantment)
            SimpleMod.LOGGER.info("Registering Enchantment: ${enchantment.name} (${enchantment.registryName})")
        }
        SimpleMod.LOGGER.info("Registered ${enchantments.size} enchantments")
    }

    fun initHandlers() {
        handlerList.forEach { it.registerToForge() }
        SimpleMod.LOGGER.info("initialized ${enchantmentList.size} enchantment handler")
    }

    fun registerEnchantments() {
        MinecraftForge.EVENT_BUS.register(this)
        initHandlers()
    }

    fun getAll(): List<Enchantment> = enchantments

    fun getByRegistryName(name: String): Enchantment? =
        enchantments.find { it.registryName.toString().equals(name, ignoreCase = true) }
}