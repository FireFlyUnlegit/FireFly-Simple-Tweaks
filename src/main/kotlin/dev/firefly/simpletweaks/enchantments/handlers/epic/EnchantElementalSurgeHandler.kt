package dev.firefly.simpletweaks.enchantments.handlers.epic

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantItemWeights
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSupportedItemTags
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.elements.ElementDamageSeam
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.player.PlayerEntity

/**
 * `elemental_surge`: `1%` per level on elemental damage the holder deals.
 *
 * Registered on [ElementDamageSeam] rather than patched into `ElementCombat`, so conversions,
 * reaction riders and aura splashes all pass through the same place.
 */
@ModEnchantment(
    id = "elemental_surge",
    category = EnchantCategory.EPIC,
    type = EnchantType.WEAPON,
    maxLevel = 3,
    weight = EnchantItemWeights.EPIC,
    damagePerLevel = 0.0,
    anvilCost = 8,
    minCostBase = 25,
    minCostPerLevel = 10,
    supportedItems = EnchantSupportedItemTags.WEAPON,
    slots = [EnchantSlot.MAINHAND],
    order = 61,
)
object EnchantElementalSurgeHandler : Listenable {

    private const val PER_LEVEL = 0.01

    override fun init() {
        ElementDamageSeam.register { attacker, _, _, amount ->
            val player = attacker as? PlayerEntity
            if (player == null) return@register amount
            val level = getItemSpecificEnchantLevel(player.mainHandStack, GeneratedEnchantments.ELEMENTAL_SURGE)
            if (level <= 0) return@register amount
            val scaled = (amount * (1.0 + PER_LEVEL * level)).toFloat()
            FSTLog.log("ElementalSurge") {
                "player=${player.name.string}, level=$level, amount=$amount->$scaled, outcome=amplified"
            }
            scaled
        }
    }
}
