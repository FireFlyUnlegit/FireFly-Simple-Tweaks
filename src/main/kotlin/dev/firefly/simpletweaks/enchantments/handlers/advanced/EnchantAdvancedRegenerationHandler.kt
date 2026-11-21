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
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.leggings
import java.util.UUID

/** The advanced form of `regeneration`: the VIII cadence, and the heal grows with missing health. */
@ModEnchantment(
    id = "advanced_regeneration",
    category = EnchantCategory.ADVANCED_UNCOMMON,
    type = EnchantType.LEGGINGS,
    maxLevel = 3,
    weight = EnchantItemWeights.ADVANCED_UNCOMMON,
    anvilCost = 8,
    minCostBase = 32,
    minCostPerLevel = 15,
    supportedItems = EnchantSupportedItemTags.LEG_ARMOR,
    slots = [EnchantSlot.LEGS],
    order = 78,
)
object EnchantAdvancedRegenerationHandler : Listenable {

    private const val BASE_LEVEL = 8
    private const val MIN_INTERVAL = 5.0
    private const val MISSING_BONUS_PER_LEVEL = 0.05f

    private val counters = mutableMapOf<UUID, Int>()

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.invalid) return
        if (e.phase != TickEvent.Phase.END) return
        val player = e.player
        if (WorldSide.isClient(player.world)) return

        val level = getItemSpecificEnchantLevel(player.leggings, GeneratedEnchantments.ADVANCED_REGENERATION)
        if (level <= 0 || player.health >= player.maxHealth) {
            counters.remove(player.uuid)
            return
        }

        val total = BASE_LEVEL + level
        val interval = maxOf(MIN_INTERVAL, 25.0 - 2.5 * total)
        val count = (counters[player.uuid] ?: 0) + 1
        if (count < interval) {
            counters[player.uuid] = count
            return
        }
        counters[player.uuid] = 0

        val missing = (1f - player.health / player.maxHealth).coerceIn(0f, 1f)
        val heal = maxOf(total / 2f, player.maxHealth * 0.00125f * total) *
            (1f + MISSING_BONUS_PER_LEVEL * level * missing)
        player.heal(heal)
        FSTLog.log("AdvancedRegeneration") {
            "player=${player.name.string}, level=$level, heal=$heal, missing=$missing, outcome=regenerated"
        }
    }
}
