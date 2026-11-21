package dev.firefly.simpletweaks.enchantments.handlers.advanced

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.attacker
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.compat.target
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantItemWeights
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSupportedItemTags
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.FireBurn
import dev.firefly.simpletweaks.util.damagesource.isAuxiliaryDamage
import dev.firefly.simpletweaks.util.combatStack
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.player.PlayerEntity

/**
 * 火焰掌控 (fire_master) advanced form — starts at fire_master's own ceiling and keeps climbing.
 *
 * The burn level handed to [FireBurn] is `6 + level`, so advanced I burns like fire_master VI; the new
 * rider is that the player hits already-burning targets harder.
 */
@ModEnchantment(
    id = "advanced_fire_master",
    category = EnchantCategory.ADVANCED_RARE,
    type = EnchantType.SWORD,
    maxLevel = 5,
    weight = EnchantItemWeights.ADVANCED_RARE,
    anvilCost = 10,
    minCostBase = 30,
    minCostPerLevel = 15,
    supportedItems = EnchantSupportedItemTags.SWORD,
    slots = [EnchantSlot.MAINHAND],
    order = 72,
)
object EnchantAdvancedFireMasterHandler : Listenable {

    /** fire_master VI. */
    const val BASE_BURN_LEVEL = 6

    /** fire_master's own self-buff length per level, in ticks. */
    const val SELF_FIRE_RESIST_TICKS = 20

    /** New rider: `+2 %` damage against targets that are already on fire, per level. */
    const val BONUS_VS_BURNING_PER_LEVEL = 0.02f

    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        if (e.source.isAuxiliaryDamage()) return
        val attacker = e.attacker as? PlayerEntity ?: return
        val target = e.target
        if (attacker === target) return

        val level = getItemSpecificEnchantLevel(
            attacker.combatStack(e.source), GeneratedEnchantments.ADVANCED_FIRE_MASTER,
        )
        if (level <= 0) return

        var amplified = false
        if (target.isOnFire) {
            e.amount *= 1f + BONUS_VS_BURNING_PER_LEVEL * level
            amplified = true
        }

        FireBurn.ignite(target, BASE_BURN_LEVEL + level, attacker)
        attacker.addStatusEffect(
            StatusEffectInstance(
                StatusEffects.FIRE_RESISTANCE,
                (BASE_BURN_LEVEL + level) * SELF_FIRE_RESIST_TICKS,
                0,
            ),
        )
        attacker.extinguish()

        FSTLog.log("AdvancedFireMaster") {
            "attacker=${attacker.name.string}, target=${target.name.string}, lvl=$level, " +
                "burn=${BASE_BURN_LEVEL + level}, amplified=$amplified, amount=${e.amount}"
        }
    }
}
