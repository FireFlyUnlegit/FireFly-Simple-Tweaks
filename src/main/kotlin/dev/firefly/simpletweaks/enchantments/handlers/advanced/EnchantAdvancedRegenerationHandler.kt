package dev.firefly.simpletweaks.enchantments.handlers.advanced

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.WorldSide
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.event.TickEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantItemWeights
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSupportedItemTags
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.TempAbsorption
import dev.firefly.simpletweaks.util.UpgradeEnchantments
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel

/** The advanced form of this enchantment: its top tier plus an effect of its own. */
@ModEnchantment(
    id = "advanced_regeneration",
    category = EnchantCategory.ADVANCED_UNCOMMON,
    type = EnchantType.LEGGINGS,
    maxLevel = 5,
    ladderBase = 8,
    weight = EnchantItemWeights.ADVANCED_UNCOMMON,
    anvilCost = 10,
    minCostBase = 30,
    minCostPerLevel = 15,
    supportedItems = EnchantSupportedItemTags.LEG_ARMOR,
    slots = [EnchantSlot.LEGS],
    order = 78,
)
object EnchantAdvancedRegenerationHandler : Listenable {
    private const val PERIOD = 40

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        val player = e.player
        if (WorldSide.isClient(player.world)) return
        if (player.age % PERIOD != 0) return
        val level = UpgradeEnchantments.ladderLevelOf(player.getEquippedStack(net.minecraft.entity.EquipmentSlot.LEGS), GeneratedEnchantments.ADVANCED_REGENERATION)
        if (level <= 0) return

        val missing = player.maxHealth - player.health
        if (missing > 0f) {
            // Rapid mending while hurt, and it stops the moment the bar is full.
            // Direct write: bypasses the healing bonus, which used to multiply every trickle tick (uild=devVer.1.0.6).
            player.health = (player.health + minOf(missing, level.toFloat())).coerceAtMost(player.maxHealth)
        } else {
            // At full health the same trickle is banked as hits, not wasted.
            if (TempAbsorption.peek(player) == null) TempAbsorption.grant(player, 2f * level, 1)
        }

        FSTLog.log("AdvancedRegenerationHandler") {
            "player=${player.name.string}, level=$level, missing=$missing, outcome=tick"
        }
    }
}
