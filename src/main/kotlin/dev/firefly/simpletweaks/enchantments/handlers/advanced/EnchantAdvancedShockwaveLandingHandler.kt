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
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.grantAbsorption
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.player.PlayerEntity
/** The advanced form of this enchantment: its top tier plus an effect of its own. */
@ModEnchantment(
    id = "advanced_shockwave_landing",
    category = EnchantCategory.ADVANCED_LEGENDARY,
    type = EnchantType.ARMOR,
    maxLevel = 4,
    weight = EnchantItemWeights.ADVANCED_LEGENDARY,
    damagePerLevel = 0.0,
    anvilCost = 12,
    minCostBase = 35,
    minCostPerLevel = 15,
    supportedItems = EnchantSupportedItemTags.ARMOR,
    slots = [EnchantSlot.ARMOR],
    order = 93,
)
object EnchantAdvancedShockwaveLandingHandler : Listenable {

    private const val BASE_LEVEL = 3

    @SubscribeEvent
    fun onLivingDamage(e: LivingDamageEvent) {
        if (e.invalid) return
        val player = e.entityLiving as? PlayerEntity ?: return
        var level = 0
        for (slot in EquipmentSlot.entries) {
            if (slot.type != EquipmentSlot.Type.HUMANOID_ARMOR) continue
            level += getItemSpecificEnchantLevel(player.getEquippedStack(slot), GeneratedEnchantments.ADVANCED_SHOCKWAVE_LANDING)
        }
        if (level <= 0) return
        val before = e.amount
        e.amount *= (1f - 0.03f * (BASE_LEVEL + level)).coerceAtLeast(0f)
        // The ground answers for you: everything within three blocks takes the landing.
        if (before > 0f) {
            val blast = before * 0.5f * (BASE_LEVEL + level)
            val nearby = player.world.getOtherEntities(player, player.boundingBox.expand(3.0)) { other ->
                other is LivingEntity && other !== player && other.isAlive && other !is PlayerEntity
            }
            for (other in nearby) (other as LivingEntity).damageElemental(player, ModDamageTypes.TRUE_DAMAGE, blast)
            if (nearby.isNotEmpty()) player.addStatusEffect(StatusEffectInstance(StatusEffects.RESISTANCE, 20, 0))
        }
        FSTLog.log("AdvancedShockwaveLandingHandler") { "player=${player.name.string}, level=$level, damage=$before->${e.amount}, outcome=applied" }
    }
}