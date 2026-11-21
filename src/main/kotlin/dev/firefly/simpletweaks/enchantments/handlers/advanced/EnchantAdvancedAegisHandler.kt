package dev.firefly.simpletweaks.enchantments.handlers.advanced

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.WorldSide
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.event.PlayerEvent
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
import dev.firefly.simpletweaks.util.absorptionHeadroomId
import dev.firefly.simpletweaks.util.chestplate
import dev.firefly.simpletweaks.util.ensureAbsorptionHeadroom
import dev.firefly.simpletweaks.util.TempAbsorption
import dev.firefly.simpletweaks.util.UpgradeEnchantments
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.grantAbsorption
import dev.firefly.simpletweaks.util.removeAbsorptionHeadroom
import java.util.UUID

/** The advanced form of `aegis`: a bigger absorption ceiling that also refills itself. */
@ModEnchantment(
    id = "advanced_aegis",
    category = EnchantCategory.ADVANCED_EPIC,
    type = EnchantType.CHESTPLATE,
    maxLevel = 5,
    ladderBase = 3,
    weight = EnchantItemWeights.ADVANCED_EPIC,
    anvilCost = 12,
    minCostBase = 35,
    minCostPerLevel = 15,
    supportedItems = EnchantSupportedItemTags.CHEST_ARMOR,
    slots = [EnchantSlot.CHEST],
    order = 76,
)
object EnchantAdvancedAegisHandler : Listenable {

    private const val HEARTS_PER_LEVEL = 2f
    private const val GRANT_INTERVAL = 100

    private val lastGrant = mutableMapOf<UUID, Int>()

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.invalid) return
        if (e.phase != TickEvent.Phase.END) return
        val player = e.player
        if (WorldSide.isClient(player.world)) return

        val level = getItemSpecificEnchantLevel(player.chestplate, GeneratedEnchantments.ADVANCED_AEGIS)
        val headroomId = absorptionHeadroomId("advanced_aegis")
        if (level <= 0) {
            lastGrant.remove(player.uuid)
            removeAbsorptionHeadroom(player, headroomId)
            return
        }

        val headroom = HEARTS_PER_LEVEL * 2f * level
        ensureAbsorptionHeadroom(player, headroomId, headroom)

        val last = lastGrant[player.uuid] ?: 0
        if (player.age - last < GRANT_INTERVAL) return
        lastGrant[player.uuid] = player.age

        val granted = grantAbsorption(player, headroomId, HEARTS_PER_LEVEL * level, headroom)
        FSTLog.log("AdvancedAegis") {
            "player=${player.name.string}, level=$level, granted=$granted, outcome=shield-refilled"
        }
    }
    @SubscribeEvent
    fun onLogout(e: PlayerEvent.PlayerLoggedOutEvent) {
        lastGrant.remove(e.player.uuid)
        removeAbsorptionHeadroom(e.player, absorptionHeadroomId("advanced_aegis"))
        TempAbsorption.clear(e.player)
    }
}