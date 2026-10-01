package dev.firefly.simpletweaks.core

import dev.firefly.simpletweaks.SimpleTweaks
import dev.firefly.simpletweaks.enchantments.common.*
import dev.firefly.simpletweaks.enchantments.epic.*
import dev.firefly.simpletweaks.enchantments.handlers.common.*
import dev.firefly.simpletweaks.enchantments.handlers.epic.*
import dev.firefly.simpletweaks.enchantments.handlers.legendary.*
import dev.firefly.simpletweaks.enchantments.handlers.mystery.EnchantCelestialBlessingHandler
import dev.firefly.simpletweaks.enchantments.handlers.mythic.EnchantDeathProtectionHandler
import dev.firefly.simpletweaks.enchantments.handlers.mythic.EnchantHealingBladeHandler
import dev.firefly.simpletweaks.enchantments.handlers.mythic.EnchantInfinitePowerHandler
import dev.firefly.simpletweaks.enchantments.handlers.rare.*
import dev.firefly.simpletweaks.enchantments.handlers.uncommon.*
import dev.firefly.simpletweaks.enchantments.legendary.*
import dev.firefly.simpletweaks.enchantments.mystery.EnchantCelestialBlessing
import dev.firefly.simpletweaks.enchantments.mythic.EnchantDeathProtection
import dev.firefly.simpletweaks.enchantments.mythic.EnchantHealingBlade
import dev.firefly.simpletweaks.enchantments.mythic.EnchantInfinitePower
import dev.firefly.simpletweaks.enchantments.rare.*
import dev.firefly.simpletweaks.enchantments.uncommon.*
import net.minecraft.enchantment.Enchantment
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.RegistryEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object EnchantmentManager {

    private val enchantments = mutableListOf<Enchantment>()

    private val enchantmentList = listOf(
        EnchantInfinitePower,
        EnchantDoubleStrike,
        EnchantFlight,
        EnchantItemFixer,
        EnchantBloodLust,
        EnchantVoidProtection,
        EnchantHealingBlade,
        EnchantCrit,
        EnchantCritDamage,
        EnchantHealer,
        EnchantExecute,
        EnchantDoubleCrit,
        EnchantEffectBonus,
        EnchantImmortal,
        EnchantSaturation,
        EnchantAcidAttack,
        EnchantArmorBreaker,
        EnchantAssassin,
        EnchantDamageLimiter,
        EnchantAoeAttack,
        EnchantChargedStrike,
        EnchantMotionBonus,
        EnchantDeathProtection,
        EnchantRegeneration,
        EnchantCombo,
        EnchantSwiftSneak,
        EnchantCelestialBlessing,
        EnchantTrueDamage,
        EnchantFireMaster,
        EnchantSuperKnockback,
        EnchantVitality,
        EnchantAntiKnockback,
        EnchantDamageReduction,
        EnchantExtraArmor,
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
        EnchantMotionBonusHandler,
        EnchantDeathProtectionHandler,
        EnchantRegenerationHandler,
        EnchantComboHandler,
        EnchantSwiftSneakHandler,
        EnchantCelestialBlessingHandler,
        EnchantTrueDamageHandler,
        EnchantFireMasterHandler,
        EnchantSuperKnockbackHandler,
        EnchantVitalityHandler,
        EnchantAntiKnockbackHandler,
        EnchantDamageReductionHandler,
        EnchantExtraArmorHandler,
    )

    @SubscribeEvent
    fun registerEnchantments(event: RegistryEvent.Register<Enchantment>) {
        enchantmentList.sortedByDescending { it.category.rarity }.forEach { enchantment ->
            enchantments.add(enchantment)
            event.registry.register(enchantment)
            SimpleTweaks.LOGGER.info("Registering Enchantment: ${enchantment.name} (${enchantment.registryName})")
        }
        SimpleTweaks.LOGGER.info("Registered ${enchantments.size} enchantments")
    }

    fun initHandlers() {
        handlerList.forEach { it.registerToForge() }
        SimpleTweaks.LOGGER.info("initialized ${enchantmentList.size} enchantment handler")
    }

    fun registerEnchantments() {
        MinecraftForge.EVENT_BUS.register(this)
        initHandlers()
    }

    fun getAll(): List<Enchantment> = enchantments

    fun getByRegistryName(name: String): Enchantment? =
        enchantments.find { it.registryName.toString().equals(name, ignoreCase = true) }
}