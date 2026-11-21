package dev.firefly.simpletweaks.enchantments.handlers.advanced

import dev.firefly.simpletweaks.compat.FSTLog
import java.util.UUID
import net.minecraft.entity.LivingEntity
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
    id = "advanced_kill_aura",
    category = EnchantCategory.ADVANCED_MYTHIC,
    type = EnchantType.WEAPON,
    maxLevel = 3,
    ladderBase = 6,
    weight = EnchantItemWeights.ADVANCED_MYTHIC,
    anvilCost = 12,
    minCostBase = 35,
    minCostPerLevel = 15,
    supportedItems = EnchantSupportedItemTags.WEAPON,
    slots = [EnchantSlot.MAINHAND],
    order = 95,
)
object EnchantAdvancedKillAuraHandler : Listenable {


    @SubscribeEvent
    fun onLivingDamage(e: LivingDamageEvent) {
        if (e.invalid) return
        if (e.source.isAuxiliaryDamage()) return
        val player = e.attacker as? PlayerEntity ?: return
        val victim = e.entityLiving
        if (victim === player) return
        val level = UpgradeEnchantments.ladderLevelOf(player.combatStack(e.source), GeneratedEnchantments.ADVANCED_KILL_AURA)
        if (level <= 0) return
        val before = e.amount
        // The aura keeps swinging: bleed `level` extra targets for a fraction of this hit.
        val nearby = player.world.getOtherEntities(player, victim.boundingBox.expand(4.0)) { other ->
            other is LivingEntity && other !== victim && other !== player && other.isAlive && other !is PlayerEntity
        }
        var struck = 0
        for (other in nearby) {
            if (struck >= level) break
            (other as LivingEntity).damageElemental(player, ModDamageTypes.TRUE_DAMAGE, before * 0.25f)
            struck++
        }
        if (struck > 0) player.addStatusEffect(StatusEffectInstance(StatusEffects.SPEED, 20, 0))
        FSTLog.log("AdvancedKillAuraHandler") { "player=${player.name.string}, level=$level, damage=$before->${e.amount}, outcome=applied" }
    }
}