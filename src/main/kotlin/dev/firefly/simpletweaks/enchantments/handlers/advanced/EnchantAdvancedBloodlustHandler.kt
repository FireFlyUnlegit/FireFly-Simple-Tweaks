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
import dev.firefly.simpletweaks.util.TempAbsorption
import dev.firefly.simpletweaks.util.combatStack
import dev.firefly.simpletweaks.util.UpgradeEnchantments
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.player.PlayerEntity

/** The advanced form of this enchantment: its top tier plus an effect of its own. */
@ModEnchantment(
    id = "advanced_bloodlust",
    category = EnchantCategory.ADVANCED_UNCOMMON,
    type = EnchantType.WEAPON,
    maxLevel = 3,
    ladderBase = 5,
    weight = EnchantItemWeights.ADVANCED_UNCOMMON,
    damagePerLevel = 0.0,
    anvilCost = 12,
    minCostBase = 35,
    minCostPerLevel = 15,
    supportedItems = EnchantSupportedItemTags.WEAPON,
    slots = [EnchantSlot.MAINHAND],
    order = 82,
)
object EnchantAdvancedBloodlustHandler : Listenable {


    @SubscribeEvent
    fun onLivingDamage(e: LivingDamageEvent) {
        if (e.invalid) return
        if (e.source.isAuxiliaryDamage()) return
        val player = e.attacker as? PlayerEntity ?: return
        val victim = e.entityLiving
        if (victim === player) return
        val level = UpgradeEnchantments.ladderLevelOf(player.combatStack(e.source), GeneratedEnchantments.ADVANCED_BLOODLUST)
        if (level <= 0) return
        val before = e.amount
        val missing = 1f - (player.health / player.maxHealth).coerceIn(0f, 1f)
        e.amount *= 1f + 0.02f * level * (0.5f + missing)

        // Drains a share of the swing; anything above the health bar becomes a one-hit shield.
        val drain = before * 0.012f * level
        val room = player.maxHealth - player.health
        val healed = minOf(drain, room).coerceAtLeast(0f)
        if (healed > 0f) player.heal(healed)
        val overflow = drain - healed
        if (overflow > 0.5f) TempAbsorption.grant(player, overflow, 1)

        FSTLog.log("AdvancedBloodlustHandler") {
            "player=${player.name.string}, level=$level, damage=$before->${e.amount}, drained=$healed, shield=$overflow"
        }
    }
}
