package dev.firefly.simplemod.core

import dev.firefly.simplemod.SimpleMod
import dev.firefly.simplemod.enchantments.EnchantBloodLust
import dev.firefly.simplemod.enchantments.EnchantCrit
import dev.firefly.simplemod.enchantments.EnchantCritDamage
import dev.firefly.simplemod.enchantments.EnchantFlight
import dev.firefly.simplemod.enchantments.EnchantHealer
import dev.firefly.simplemod.enchantments.EnchantHealingBlade
import dev.firefly.simplemod.enchantments.EnchantInfinitePower
import dev.firefly.simplemod.enchantments.EnchantItemFixer
import dev.firefly.simplemod.enchantments.EnchantVoidProtection
import dev.firefly.simplemod.enchantments.enchantment_handlers.EnchantBloodLustHandler
import dev.firefly.simplemod.enchantments.enchantment_handlers.EnchantCritDamageHandler
import dev.firefly.simplemod.enchantments.enchantment_handlers.EnchantCritHandler
import dev.firefly.simplemod.enchantments.enchantment_handlers.EnchantFlightHandler
import dev.firefly.simplemod.enchantments.enchantment_handlers.EnchantHealerHandler
import dev.firefly.simplemod.enchantments.enchantment_handlers.EnchantHealingBladeHandler
import dev.firefly.simplemod.enchantments.enchantment_handlers.EnchantInfinitePowerHandler
import dev.firefly.simplemod.enchantments.enchantment_handlers.EnchantItemFixerHandler
import dev.firefly.simplemod.enchantments.enchantment_handlers.EnchantVoidProtectionHandler
import net.minecraft.enchantment.Enchantment
import net.minecraftforge.event.RegistryEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

/**
 * 附魔管理器
 * 统一管理所有自定义附魔的注册和初始化
 */
@Mod.EventBusSubscriber

object EnchantmentManager {

    private val enchantments = mutableListOf<Enchantment>()

    private val enchantmentList = listOf(
        EnchantInfinitePower.INSTANCE,
        EnchantFlight.INSTANCE,
        EnchantItemFixer.INSTANCE,
        EnchantBloodLust.INSTANCE,
        EnchantVoidProtection.INSTANCE,
        EnchantHealingBlade.INSTANCE,
        EnchantCrit.INSTANCE,
        EnchantCritDamage.INSTANCE,
        EnchantHealer.INSTANCE,
    )
    private val handlerList = listOf(
        EnchantInfinitePowerHandler,
        EnchantFlightHandler,
        EnchantItemFixerHandler,
        EnchantBloodLustHandler,
        EnchantVoidProtectionHandler,
        EnchantHealingBladeHandler,
        EnchantCritHandler,
        EnchantCritDamageHandler,
        EnchantHealerHandler,
    )

    /**
     * 注册所有附魔到 Forge 注册表
     * 由 @SubscribeEvent 自动调用，不需要手动调用
     */
    @SubscribeEvent
    fun registerEnchantments(event: RegistryEvent.Register<Enchantment>) {
        enchantmentList.forEach { enchantment ->
            enchantments.add(enchantment)
            event.registry.register(enchantment)
            SimpleMod.LOGGER.info("Registering Enchantment: ${enchantment.name} (${enchantment.registryName})")
        }
        SimpleMod.LOGGER.info("Registered ${enchantments.size} enchantments")
    }

    /**
     * 初始化所有附魔的处理器
     * 在 SimpleMod.init() 中调用
     */
    fun initHandlers() {
        handlerList.forEach { it.registerToForge() }
        SimpleMod.LOGGER.info("initialized ${enchantmentList.size} enchantment handler")
    }

    fun getAll(): List<Enchantment> = enchantments

    fun getByRegistryName(name: String): Enchantment? =
        enchantments.find { it.registryName.toString().equals(name, ignoreCase = true) }
}