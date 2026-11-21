package dev.firefly.simpletweaks.enchantments.handlers.epic

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.WorldSide
import dev.firefly.simpletweaks.compat.event.LivingDamageEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantItemWeights
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSupportedItemTags
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.EnchantPresence
import net.minecraft.entity.player.PlayerEntity

/**
 * `bulwark`: `1%` per level off every hit that lands on the wearer, applied after armour and after
 * `arcane_echo`'s own final reduction, so the two multiply instead of adding up to a free immunity.
 */
@ModEnchantment(
    id = "bulwark",
    category = EnchantCategory.EPIC,
    type = EnchantType.ARMOR,
    maxLevel = 3,
    weight = EnchantItemWeights.EPIC,
    anvilCost = 8,
    minCostBase = 25,
    minCostPerLevel = 10,
    supportedItems = EnchantSupportedItemTags.ARMOR,
    slots = [EnchantSlot.ARMOR],
    order = 63,
)
object EnchantBulwarkHandler : Listenable {

    private const val PER_LEVEL = 0.01

    @SubscribeEvent(priority = 45)
    fun onLivingDamage(e: LivingDamageEvent) {
        if (e.invalid) return
        if (WorldSide.isClient(e.entityLiving.world)) return
        val victim = e.entityLiving as? PlayerEntity ?: return
        val level = EnchantPresence.armorLevelOf(victim, GeneratedEnchantments.BULWARK)
        if (level <= 0) return
        val before = e.amount
        e.amount = (e.amount * (1.0 - PER_LEVEL * level)).toFloat().coerceAtLeast(0f)
        FSTLog.log("Bulwark") {
            "victim=${victim.name.string}, level=$level, amount=$before->${e.amount}, outcome=reduced"
        }
    }
}
