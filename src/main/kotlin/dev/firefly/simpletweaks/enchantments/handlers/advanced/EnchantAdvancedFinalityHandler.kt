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

/** The advanced form of this enchantment: its top tier plus an effect of its own. */
@ModEnchantment(
    id = "advanced_finality",
    category = EnchantCategory.ADVANCED_EPIC,
    type = EnchantType.WEAPON,
    maxLevel = 3,
    ladderBase = 3,
    weight = EnchantItemWeights.ADVANCED_EPIC,
    anvilCost = 14,
    minCostBase = 40,
    minCostPerLevel = 20,
    supportedItems = EnchantSupportedItemTags.WEAPON,
    slots = [EnchantSlot.MAINHAND],
    order = 96,
)
object EnchantAdvancedFinalityHandler : Listenable {

    private const val FINISHER_FRACTION = 0.20f

    @SubscribeEvent
    fun onLivingDamage(e: LivingDamageEvent) {
        if (e.invalid) return
        if (e.source.isAuxiliaryDamage()) return
        val player = e.attacker as? PlayerEntity ?: return
        val victim = e.entityLiving
        if (victim === player) return
        val level = getItemSpecificEnchantLevel(player.combatStack(e.source), GeneratedEnchantments.ADVANCED_FINALITY)
        if (level <= 0) return

        val before = e.amount
        e.amount *= 1f + 0.01f * level
        if (victim.getHealth() >= victim.getMaxHealth()) e.amount *= 1f + 0.02f * level

        // Below the line the blade stops haggling with armour: the remainder is simply true damage.
        val fraction = victim.getHealth() / victim.getMaxHealth().coerceAtLeast(1f)
        if (fraction <= FINISHER_FRACTION) {
            val finish = before * 0.10f * level
            val landed = victim.damageElemental(player, ModDamageTypes.TRUE_DAMAGE, finish)
            FSTLog.log("AdvancedFinalityHandler") {
                "player=${player.name.string}, level=$level, finish=$finish, landed=$landed, outcome=finisher"
            }
            return
        }

        FSTLog.log("AdvancedFinalityHandler") {
            "player=${player.name.string}, level=$level, damage=$before->${e.amount}, outcome=amplified"
        }
    }
}
