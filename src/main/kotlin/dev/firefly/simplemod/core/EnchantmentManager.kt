package dev.firefly.simplemod.core

import dev.firefly.simplemod.SimpleMod
import dev.firefly.simplemod.enchantments.common.EnchantAcidAttack
import dev.firefly.simplemod.enchantments.common.EnchantArmorBreaker
import dev.firefly.simplemod.enchantments.common.EnchantMotionBonus
import dev.firefly.simplemod.enchantments.common.EnchantVoidProtection
import dev.firefly.simplemod.enchantments.epic.EnchantChargedStrike
import dev.firefly.simplemod.enchantments.epic.EnchantCritDamage
import dev.firefly.simplemod.enchantments.handlers.common.EnchantAcidAttackHandler
import dev.firefly.simplemod.enchantments.handlers.common.EnchantArmorBreakerHandler
import dev.firefly.simplemod.enchantments.handlers.common.EnchantMotionBonusHandler
import dev.firefly.simplemod.enchantments.handlers.common.EnchantVoidProtectionHandler
import dev.firefly.simplemod.enchantments.handlers.epic.EnchantChargedStrikeHandler
import dev.firefly.simplemod.enchantments.handlers.epic.EnchantCritDamageHandler
import dev.firefly.simplemod.enchantments.handlers.legendary.*
import dev.firefly.simplemod.enchantments.handlers.mystery.EnchantCelestialBlessingHandler
import dev.firefly.simplemod.enchantments.handlers.mythic.EnchantDeathProtectionHandler
import dev.firefly.simplemod.enchantments.handlers.mythic.EnchantHealingBladeHandler
import dev.firefly.simplemod.enchantments.handlers.mythic.EnchantInfinitePowerHandler
import dev.firefly.simplemod.enchantments.handlers.rare.EnchantAssassinHandler
import dev.firefly.simplemod.enchantments.handlers.rare.EnchantExecuteHandler
import dev.firefly.simplemod.enchantments.handlers.rare.EnchantImmortalHandler
import dev.firefly.simplemod.enchantments.handlers.rare.EnchantItemFixerHandler
import dev.firefly.simplemod.enchantments.handlers.uncommon.*
import dev.firefly.simplemod.enchantments.legendary.*
import dev.firefly.simplemod.enchantments.mystery.EnchantCelestialBlessing
import dev.firefly.simplemod.enchantments.mythic.EnchantDeathProtection
import dev.firefly.simplemod.enchantments.mythic.EnchantHealingBlade
import dev.firefly.simplemod.enchantments.mythic.EnchantInfinitePower
import dev.firefly.simplemod.enchantments.rare.EnchantAssassin
import dev.firefly.simplemod.enchantments.rare.EnchantExecute
import dev.firefly.simplemod.enchantments.rare.EnchantImmortal
import dev.firefly.simplemod.enchantments.rare.EnchantItemFixer
import dev.firefly.simplemod.enchantments.uncommon.*
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
    )

    @SubscribeEvent
    fun registerEnchantments(event: RegistryEvent.Register<Enchantment>) {
        enchantmentList.sortedByDescending { it.category.rarity }.forEach { enchantment ->
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