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
import dev.firefly.simpletweaks.util.HealLock
import dev.firefly.simpletweaks.util.combatStack
import dev.firefly.simpletweaks.util.damagesource.ModDamageTypes
import dev.firefly.simpletweaks.util.damagesource.damageElemental
import dev.firefly.simpletweaks.util.damagesource.isAuxiliaryDamage
import dev.firefly.simpletweaks.util.UpgradeEnchantments
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.player.PlayerEntity

/** The advanced form of this enchantment: its top tier plus an effect of its own. */
@ModEnchantment(
    id = "advanced_frostbite",
    category = EnchantCategory.ADVANCED_RARE,
    type = EnchantType.WEAPON,
    maxLevel = 3,
    ladderBase = 3,
    weight = EnchantItemWeights.ADVANCED_RARE,
    anvilCost = 14,
    minCostBase = 35,
    minCostPerLevel = 15,
    supportedItems = EnchantSupportedItemTags.WEAPON,
    slots = [EnchantSlot.MAINHAND],
    order = 87,
)
object EnchantAdvancedFrostbiteHandler : Listenable {

    private const val FREEZE_TICKS = 40L

    @SubscribeEvent
    fun onLivingDamage(e: LivingDamageEvent) {
        if (e.invalid) return
        if (e.source.isAuxiliaryDamage()) return
        val player = e.attacker as? PlayerEntity ?: return
        val victim = e.entityLiving
        if (victim === player) return
        val level = getItemSpecificEnchantLevel(player.combatStack(e.source), GeneratedEnchantments.ADVANCED_FROSTBITE)
        if (level <= 0) return

        val before = e.amount
        val chilled = victim.getStatusEffect(StatusEffects.SLOWNESS) != null
        victim.addStatusEffect(StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1))
        e.amount *= 1f + 0.02f * level * (if (chilled) 2f else 1f)

        // Hitting something already chilled freezes the wound shut: no healing, and a brittle crack.
        if (chilled) {
            HealLock.lock(victim, FREEZE_TICKS)
            val shatter = before * 0.04f * level
            val landed = victim.damageElemental(player, ModDamageTypes.TRUE_DAMAGE, shatter)
            FSTLog.log("AdvancedFrostbiteHandler") {
                "player=${player.name.string}, level=$level, shatter=$shatter, landed=$landed, outcome=frozen"
            }
            return
        }

        FSTLog.log("AdvancedFrostbiteHandler") {
            "player=${player.name.string}, level=$level, damage=$before->${e.amount}, outcome=chilled"
        }
    }
}
