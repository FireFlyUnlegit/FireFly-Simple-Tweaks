package dev.firefly.simpletweaks.enchantments.handlers.mythic

import dev.firefly.simpletweaks.compat.WorldSide
import dev.firefly.simpletweaks.compat.attacker
import dev.firefly.simpletweaks.compat.event.LivingEvent
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.compat.target
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantItemWeights
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSupportedItemTags
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.DotAcceleration
import dev.firefly.simpletweaks.util.combatStack
import dev.firefly.simpletweaks.util.damagesource.isAuxiliaryDamage
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.player.PlayerEntity

@ModEnchantment(
    id = "catalyze",
    category = EnchantCategory.MYTHIC,
    type = EnchantType.SWORD,
    maxLevel = 3,
    weight = EnchantItemWeights.MYTHIC,
    anvilCost = 12,
    minCostBase = 30,
    minCostPerLevel = 20,
    supportedItems = EnchantSupportedItemTags.SWORD,
    slots = [EnchantSlot.MAINHAND],
    order = 56,
)
object EnchantCatalyzeHandler : Listenable {

    private const val DURATION_TICKS = 100L

    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        if (e.source.isAuxiliaryDamage()) return
        val attacker = e.attacker as? PlayerEntity ?: return
        val target = e.target
        if (attacker === target) return

        val level = getItemSpecificEnchantLevel(attacker.combatStack(e.source), GeneratedEnchantments.CATALYZE)
        if (level <= 0) return

        DotAcceleration.apply(target, level, DURATION_TICKS, attacker)
    }

    @SubscribeEvent
    fun onLivingUpdate(e: LivingEvent.LivingUpdateEvent) {
        val entity = e.entityLiving
        if (WorldSide.isClient(entity.world)) return
        DotAcceleration.tick(entity)
    }
}
