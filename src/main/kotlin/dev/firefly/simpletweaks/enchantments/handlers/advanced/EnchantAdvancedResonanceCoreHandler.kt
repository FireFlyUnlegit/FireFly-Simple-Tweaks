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
import dev.firefly.simpletweaks.util.elements.ElementAuras
import dev.firefly.simpletweaks.util.UpgradeEnchantments
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.player.PlayerEntity

/** The advanced form of this enchantment: its top tier plus an effect of its own. */
@ModEnchantment(
    id = "advanced_resonance_core",
    category = EnchantCategory.ADVANCED_MYSTERY,
    type = EnchantType.WEAPON,
    maxLevel = 4,
    ladderBase = 3,
    weight = EnchantItemWeights.ADVANCED_MYSTERY,
    anvilCost = 18,
    minCostBase = 45,
    minCostPerLevel = 20,
    supportedItems = EnchantSupportedItemTags.WEAPON,
    slots = [EnchantSlot.MAINHAND],
    order = 102,
)
object EnchantAdvancedResonanceCoreHandler : Listenable {

    @SubscribeEvent
    fun onLivingDamage(e: LivingDamageEvent) {
        if (e.invalid) return
        if (e.source.isAuxiliaryDamage()) return
        val player = e.attacker as? PlayerEntity ?: return
        val victim = e.entityLiving
        if (victim === player) return
        val level = getItemSpecificEnchantLevel(player.combatStack(e.source), GeneratedEnchantments.ADVANCED_RESONANCE_CORE)
        if (level <= 0) return
        if (ElementAuras.peek(victim) == null) return

        // The core refuses to wait for a reaction: it eats whatever element is already there and
        // spends it as raw damage, so nothing is left for the reaction table to fire on.
        val before = e.amount
        ElementAuras.clear(victim)
        val burst = before * 0.06f * level
        val landed = victim.damageElemental(player, ModDamageTypes.TRUE_DAMAGE, burst)
        FSTLog.log("AdvancedResonanceCoreHandler") {
            "player=${player.name.string}, level=$level, burst=$burst, landed=$landed, outcome=discharged"
        }
    }
}
