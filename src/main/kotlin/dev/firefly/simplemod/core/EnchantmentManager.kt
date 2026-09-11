package dev.firefly.simplemod.core

import dev.firefly.simplemod.SimpleMod
import dev.firefly.simplemod.enchantments.EnchantAcidAttack
import dev.firefly.simplemod.enchantments.EnchantArmorBreaker
import dev.firefly.simplemod.enchantments.EnchantAssassin
import dev.firefly.simplemod.enchantments.EnchantBloodLust
import dev.firefly.simplemod.enchantments.EnchantCrit
import dev.firefly.simplemod.enchantments.EnchantCritDamage
import dev.firefly.simplemod.enchantments.EnchantDoubleCrit
import dev.firefly.simplemod.enchantments.EnchantDoubleStrike
import dev.firefly.simplemod.enchantments.EnchantEffectBonus
import dev.firefly.simplemod.enchantments.EnchantExecute
import dev.firefly.simplemod.enchantments.EnchantFlight
import dev.firefly.simplemod.enchantments.EnchantHealer
import dev.firefly.simplemod.enchantments.EnchantHealingBlade
import dev.firefly.simplemod.enchantments.EnchantImmortal
import dev.firefly.simplemod.enchantments.EnchantInfinitePower
import dev.firefly.simplemod.enchantments.EnchantItemFixer
import dev.firefly.simplemod.enchantments.EnchantSaturation
import dev.firefly.simplemod.enchantments.EnchantVoidProtection
import dev.firefly.simplemod.enchantments.enchantment_handlers.EnchantAcidAttackHandler
import dev.firefly.simplemod.enchantments.enchantment_handlers.EnchantArmorBreakerHandler
import dev.firefly.simplemod.enchantments.enchantment_handlers.EnchantAssassinHandler
import dev.firefly.simplemod.enchantments.enchantment_handlers.EnchantBloodLustHandler
import dev.firefly.simplemod.enchantments.enchantment_handlers.EnchantCritDamageHandler
import dev.firefly.simplemod.enchantments.enchantment_handlers.EnchantCritHandler
import dev.firefly.simplemod.enchantments.enchantment_handlers.EnchantDoubleCritHandler
import dev.firefly.simplemod.enchantments.enchantment_handlers.EnchantDoubleStrikeHandler
import dev.firefly.simplemod.enchantments.enchantment_handlers.EnchantEffectBonusHandler
import dev.firefly.simplemod.enchantments.enchantment_handlers.EnchantExecuteHandler
import dev.firefly.simplemod.enchantments.enchantment_handlers.EnchantFlightHandler
import dev.firefly.simplemod.enchantments.enchantment_handlers.EnchantHealerHandler
import dev.firefly.simplemod.enchantments.enchantment_handlers.EnchantHealingBladeHandler
import dev.firefly.simplemod.enchantments.enchantment_handlers.EnchantImmortalHandler
import dev.firefly.simplemod.enchantments.enchantment_handlers.EnchantInfinitePowerHandler
import dev.firefly.simplemod.enchantments.enchantment_handlers.EnchantItemFixerHandler
import dev.firefly.simplemod.enchantments.enchantment_handlers.EnchantSaturationHandler
import dev.firefly.simplemod.enchantments.enchantment_handlers.EnchantVoidProtectionHandler
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