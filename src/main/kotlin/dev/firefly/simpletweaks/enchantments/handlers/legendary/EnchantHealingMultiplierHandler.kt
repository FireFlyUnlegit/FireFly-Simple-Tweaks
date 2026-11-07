package dev.firefly.simpletweaks.enchantments.handlers.legendary

import dev.firefly.simpletweaks.compat.event.LivingHealEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.*
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.chestplate
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.lossHealth

@ModEnchantment(
    "healing_multiplier",
    EnchantCategory.LEGENDARY,
    EnchantType.CHESTPLATE,
    maxLevel = 10,
    anvilCost = 10,
    minCostBase = 10,
    slots = [EnchantSlot.CHEST],
    supportedItems = EnchantSupportedItemTags.CHEST_ARMOR
)
object EnchantHealingMultiplierHandler : Listenable {
    @SubscribeEvent
    fun onLivingHeal(e: LivingHealEvent) {
        val entity = e.entityLiving
        val item = e.entityLiving.chestplate
        val lvl = getItemSpecificEnchantLevel(item, GeneratedEnchantments.HEALING_MULTIPLIER)
        val amount = e.amount
        val boostAmount = (e.amount * 0.1f * lvl) + (0.25f * lvl)
        if (lvl > 0) {
            if (amount + boostAmount <= entity.lossHealth) e.amount += boostAmount else e.amount = entity.lossHealth
        }
    }
}