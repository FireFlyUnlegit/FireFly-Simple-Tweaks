package dev.firefly.simpletweaks.enchantments.handlers.advanced

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.attacker
import dev.firefly.simpletweaks.compat.event.LivingDamageEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.util.damagesource.isAuxiliaryDamage
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantItemWeights
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSupportedItemTags
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.absorptionHeadroomId
import dev.firefly.simpletweaks.util.combatStack
import dev.firefly.simpletweaks.util.UpgradeEnchantments
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.grantAbsorption
import net.minecraft.entity.player.PlayerEntity

/** The advanced form of this enchantment: its top tier plus an effect of its own. */
@ModEnchantment(
    id = "advanced_healing_blade",
    category = EnchantCategory.ADVANCED_MYTHIC,
    type = EnchantType.SWORD,
    maxLevel = 5,
    ladderBase = 8,
    weight = EnchantItemWeights.ADVANCED_MYTHIC,
    anvilCost = 18,
    minCostBase = 45,
    minCostPerLevel = 20,
    supportedItems = EnchantSupportedItemTags.SWORD,
    slots = [EnchantSlot.MAINHAND],
    order = 97,
)
object EnchantAdvancedHealingBladeHandler : Listenable {

    private const val HEADROOM = "advanced_healing_blade"

    @SubscribeEvent
    fun onLivingDamage(e: LivingDamageEvent) {
        if (e.invalid) return
        if (e.source.isAuxiliaryDamage()) return
        val player = e.attacker as? PlayerEntity ?: return
        val victim = e.entityLiving
        if (victim === player) return
        val level = UpgradeEnchantments.ladderLevelOf(player.combatStack(e.source), GeneratedEnchantments.ADVANCED_HEALING_BLADE)
        if (level <= 0) return

        // Inherits the base blade (a share of the damage just dealt), then adds the step effect.
        val missing = (player.maxHealth - player.health).coerceAtLeast(0f)
        if (missing <= 0f) return
        val siphon = (e.amount * 0.10f).coerceAtMost(missing)
        val mending = (missing * 0.10f * level).coerceAtMost(missing - siphon)
        val heal = siphon + mending
        if (heal <= 0f) return
        player.heal(heal)

        // At full health every drop is banked instead of wasted.
        val stillMissing = (player.maxHealth - player.health).coerceAtLeast(0f)
        if (stillMissing <= 0.5f) {
            val headroom = 2f * level
            grantAbsorption(player, absorptionHeadroomId(HEADROOM), heal, headroom)
        }

        FSTLog.log("AdvancedHealingBladeHandler") {
            "player=${player.name.string}, level=$level, missing=$missing, healed=$heal"
        }
    }
}
