package dev.firefly.simpletweaks.enchantments.handlers.advanced

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.attacker
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
import dev.firefly.simpletweaks.util.combatStack
import dev.firefly.simpletweaks.util.damagesource.ModDamageTypes
import dev.firefly.simpletweaks.util.damagesource.damageElemental
import dev.firefly.simpletweaks.util.damagesource.isAuxiliaryDamage
import dev.firefly.simpletweaks.util.UpgradeEnchantments
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.player.PlayerEntity
import java.util.UUID

/** The advanced form of this enchantment: its top tier plus an effect of its own. */
@ModEnchantment(
    id = "advanced_devour",
    category = EnchantCategory.ADVANCED_MYTHIC,
    type = EnchantType.WEAPON,
    maxLevel = 5,
    ladderBase = 3,
    weight = EnchantItemWeights.ADVANCED_MYTHIC,
    anvilCost = 18,
    minCostBase = 40,
    minCostPerLevel = 20,
    supportedItems = EnchantSupportedItemTags.WEAPON,
    slots = [EnchantSlot.MAINHAND],
    order = 95,
)
object EnchantAdvancedDevourHandler : Listenable {

    private const val SWINGS_PER_BITE = 3

    private val swings = java.util.Collections.synchronizedMap(HashMap<UUID, Int>())

    @SubscribeEvent
    fun onLivingDamage(e: LivingDamageEvent) {
        if (e.invalid) return
        if (e.source.isAuxiliaryDamage()) return
        val player = e.attacker as? PlayerEntity ?: return
        val victim = e.entityLiving
        if (victim === player) return
        val level = getItemSpecificEnchantLevel(player.combatStack(e.source), GeneratedEnchantments.ADVANCED_DEVOUR)
        if (level <= 0) return

        val count = (swings[player.uuid] ?: 0) + 1
        if (count < SWINGS_PER_BITE) {
            swings[player.uuid] = count
            return
        }
        swings[player.uuid] = 0

        // Every fifth swing the stored hunger bites: an unavoidable chunk scaled by the hit itself.
        val bite = e.amount * 0.08f * level
        if (bite <= 0f) return
        val landed = victim.damageElemental(player, ModDamageTypes.TRUE_DAMAGE, bite)
        FSTLog.log("AdvancedDevourHandler") {
            "player=${player.name.string}, level=$level, bite=$bite, landed=$landed, outcome=bite"
        }
    }
}
