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
import dev.firefly.simpletweaks.util.FireBurn
import dev.firefly.simpletweaks.util.absorptionHeadroomId
import dev.firefly.simpletweaks.util.combatStack
import dev.firefly.simpletweaks.util.damagesource.ModDamageTypes
import dev.firefly.simpletweaks.util.damagesource.damageElemental
import dev.firefly.simpletweaks.util.damagesource.isAuxiliaryDamage
import dev.firefly.simpletweaks.util.UpgradeEnchantments
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.grantAbsorption
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.player.PlayerEntity
/** The advanced form of this enchantment: its top tier plus an effect of its own. */
@ModEnchantment(
    id = "advanced_midas_touch",
    category = EnchantCategory.ADVANCED_MYSTERY,
    type = EnchantType.TOOL,
    maxLevel = 3,
    ladderBase = 3,
    weight = EnchantItemWeights.ADVANCED_MYSTERY,
    damagePerLevel = 0.0,
    anvilCost = 12,
    minCostBase = 35,
    minCostPerLevel = 15,
    supportedItems = EnchantSupportedItemTags.WEAPON,
    slots = [EnchantSlot.MAINHAND],
    order = 103,
)
object EnchantAdvancedMidasTouchHandler : Listenable {


    @SubscribeEvent
    fun onLivingDamage(e: LivingDamageEvent) {
        if (e.invalid) return
        if (e.source.isAuxiliaryDamage()) return
        val player = e.attacker as? PlayerEntity ?: return
        val victim = e.entityLiving
        if (victim === player) return
        val level = getItemSpecificEnchantLevel(player.combatStack(e.source), GeneratedEnchantments.ADVANCED_MIDAS_TOUCH)
        if (level <= 0) return
        val before = e.amount
        // Gold follows the kill, not the swing: a finished target drops bottles of enchanting.
        if (victim.health - e.amount <= 0f) {
            val bottles = Math.min(level, 3)
            for (i in 0 until bottles) victim.dropStack(net.minecraft.item.ItemStack(net.minecraft.item.Items.EXPERIENCE_BOTTLE))
            player.addStatusEffect(StatusEffectInstance(StatusEffects.HASTE, 20 * 10, level - 1))
        }
        FSTLog.log("AdvancedMidasTouchHandler") { "player=${player.name.string}, level=$level, damage=$before->${e.amount}, outcome=applied" }
    }
}