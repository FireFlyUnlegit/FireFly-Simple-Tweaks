package dev.firefly.simpletweaks.enchantments.handlers.mythic

import dev.firefly.simpletweaks.compat.attacker
import dev.firefly.simpletweaks.compat.event.EventPriority
import dev.firefly.simpletweaks.compat.event.LivingDamageEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.compat.target
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantColor
import dev.firefly.simpletweaks.enchantments.annotations.EnchantItemWeights
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSupportedItemTags
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.armorToughness
import dev.firefly.simpletweaks.util.getArmorEnchantLevel
import kotlin.math.sqrt

@ModEnchantment(
    "armor_enhance",
    EnchantCategory.MYTHIC,
    EnchantType.ARMOR,
    EnchantColor.INHERIT,
    maxLevel = 8,
    EnchantItemWeights.MYTHIC,
    5,
    30,
    10,
    EnchantSupportedItemTags.ARMOR,
    slots = [EnchantSlot.ARMOR]
)
object EnchantArmorEnhanceHandler : Listenable {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onLivingDamage(e: LivingDamageEvent) {
        if (e.invalid)return

        val receiver = e.target

        val lvl = receiver.getArmorEnchantLevel(GeneratedEnchantments.ARMOR_ENHANCE)
        if (lvl > 0) {
            val armorLoss = sqrt(0.005f * receiver.armor)
            val armorToughnessLoss = sqrt(0.008f * receiver.armorToughness)
            e.amount -= ((armorLoss + armorToughnessLoss) * lvl * 0.2).toFloat().coerceAtMost(e.amount)
        }
    }
}