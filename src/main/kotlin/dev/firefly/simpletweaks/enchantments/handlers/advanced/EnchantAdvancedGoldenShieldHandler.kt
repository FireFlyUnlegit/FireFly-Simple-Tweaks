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
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.player.PlayerEntity
/** The advanced form of this enchantment: its top tier plus an effect of its own. */
@ModEnchantment(
    id = "advanced_golden_shield",
    category = EnchantCategory.ADVANCED_EPIC,
    type = EnchantType.ARMOR,
    maxLevel = 5,
    ladderBase = 3,
    weight = EnchantItemWeights.ADVANCED_EPIC,
    damagePerLevel = 0.0,
    anvilCost = 12,
    minCostBase = 35,
    minCostPerLevel = 15,
    supportedItems = EnchantSupportedItemTags.ARMOR,
    slots = [EnchantSlot.ARMOR],
    order = 88,
)
object EnchantAdvancedGoldenShieldHandler : Listenable {

    /** Absorption ceiling: four maxed pieces used to hand out sixty-odd hit points. */
    private const val MAX_SHIELD = 8f


    @SubscribeEvent
    fun onLivingDamage(e: LivingDamageEvent) {
        if (e.invalid) return
        if (e.source.isAuxiliaryDamage()) return
        val player = e.entityLiving as? PlayerEntity ?: return
        var level = 0
        for (slot in EquipmentSlot.entries) {
            if (slot.type != EquipmentSlot.Type.HUMANOID_ARMOR) continue
            level += UpgradeEnchantments.ladderLevelOf(player.getEquippedStack(slot), GeneratedEnchantments.ADVANCED_GOLDEN_SHIELD)
        }
        if (level <= 0) return
        val before = e.amount
        if (player.age % 100 == 0) {
            val shield = (0.5f * level).coerceAtMost(MAX_SHIELD)
            grantAbsorption(player, absorptionHeadroomId("advanced_golden_shield"), shield, shield)
        }
        e.amount *= (1f - 0.015f * level).coerceIn(0.25f, 1f)
        FSTLog.log("AdvancedGoldenShieldHandler") { "player=${player.name.string}, level=$level, damage=$before->${e.amount}, outcome=applied" }
    }
}