package dev.firefly.simpletweaks.enchantments.handlers.advanced

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.WorldSide
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.event.TickEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantItemWeights
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSupportedItemTags
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.UpgradeEnchantments
import dev.firefly.simpletweaks.util.absorptionHeadroomId
import dev.firefly.simpletweaks.util.grantAbsorption
import dev.firefly.simpletweaks.util.removeAbsorptionHeadroom
import dev.firefly.simpletweaks.util.chestplate
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import java.util.UUID

/**
 * The advanced form of [saturation]: keeps the bar topped like saturation VIII, then turns the
 * overflow into absorption.
 */
@ModEnchantment(
    id = "advanced_saturation",
    category = EnchantCategory.ADVANCED_UNCOMMON,
    type = EnchantType.CHESTPLATE,
    maxLevel = 3,
    ladderBase = 8,
    weight = EnchantItemWeights.ADVANCED_UNCOMMON,
    anvilCost = 8,
    minCostBase = 30,
    minCostPerLevel = 15,
    supportedItems = EnchantSupportedItemTags.CHEST_ARMOR,
    slots = [EnchantSlot.CHEST],
    order = 74,
)
object EnchantAdvancedSaturationHandler : Listenable {

    /** Ticks between two absorption grants. */
    private const val GRANT_INTERVAL = 100

    /** Hearts granted per level, on every grant. */
    private const val HEARTS_PER_LEVEL = 1f

    private val RULE = UpgradeEnchantments.rules().first { it.upgrade == GeneratedEnchantments.ADVANCED_SATURATION }

    private val lastGrant = mutableMapOf<UUID, Int>()

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.invalid) return
        if (e.phase != TickEvent.Phase.END) return
        val p = e.player
        if (WorldSide.isClient(p.world)) return

        val level = UpgradeEnchantments.ladderLevelOf(p.chestplate, GeneratedEnchantments.ADVANCED_SATURATION)
        if (level <= 0) {
            lastGrant.remove(p.uuid)
            removeAbsorptionHeadroom(p, absorptionHeadroomId("advanced_saturation"))
            return
        }

        val food = p.hungerManager
        if (food.foodLevel < 20) food.foodLevel = 20
        if (food.saturationLevel < 10f) food.saturationLevel = 10f

        val last = lastGrant[p.uuid] ?: 0
        if (p.age - last < GRANT_INTERVAL) return
        lastGrant[p.uuid] = p.age

        val wanted = HEARTS_PER_LEVEL * level * 2f
        // Never stack: the point is a topped-up shield, not an ever-growing one.
        if (p.absorptionAmount >= wanted) return
        val granted = grantAbsorption(
            p,
            absorptionHeadroomId("advanced_saturation"),
            wanted,
            wanted,
        )
        FSTLog.log("AdvancedSaturation") {
            "player=${p.name.string}, level=$level, granted=$granted, outcome=overflow-absorption"
        }
    }
}
