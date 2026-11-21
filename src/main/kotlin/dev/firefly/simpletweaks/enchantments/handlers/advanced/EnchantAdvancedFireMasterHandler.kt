package dev.firefly.simpletweaks.enchantments.handlers.advanced

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.attacker
import dev.firefly.simpletweaks.compat.event.LivingDeathEvent
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
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
import dev.firefly.simpletweaks.util.combatStack
import dev.firefly.simpletweaks.util.damagesource.damageElemental
import dev.firefly.simpletweaks.util.elements.ElementType
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.Box

/** The advanced form of this enchantment: its top tier plus an effect of its own. */
@ModEnchantment(
    id = "advanced_fire_master",
    category = EnchantCategory.ADVANCED_RARE,
    type = EnchantType.SWORD,
    maxLevel = 5,
    weight = EnchantItemWeights.ADVANCED_RARE,
    anvilCost = 14,
    minCostBase = 35,
    minCostPerLevel = 15,
    supportedItems = EnchantSupportedItemTags.SWORD,
    slots = [EnchantSlot.MAINHAND],
    order = 72,
)
object EnchantAdvancedFireMasterHandler : Listenable {

    private const val BASE_BURN_LEVEL = 6
    private const val BURST_RADIUS = 4.0

    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        val player = e.attacker as? PlayerEntity ?: return
        val victim = e.target
        if (victim === player) return
        val level = getItemSpecificEnchantLevel(player.combatStack(e.source), GeneratedEnchantments.ADVANCED_FIRE_MASTER)
        if (level <= 0) return

        val burnLevel = BASE_BURN_LEVEL + level
        if (victim.fireTicks > 0) e.amount *= 1f + 0.02f * level
        FireBurn.ignite(victim, burnLevel, player)
        player.addStatusEffect(StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, burnLevel * 20, 0))
        if (player.fireTicks > 0) player.extinguish()

        FSTLog.log("AdvancedFireMasterHandler") {
            "player=${player.name.string}, level=$level, burn=$burnLevel, outcome=ignited"
        }
    }

    @SubscribeEvent
    fun onLivingDeath(e: LivingDeathEvent) {
        val dying = e.entityLiving
        if (dying.fireTicks <= 0) return
        val player = e.source.attacker as? PlayerEntity ?: return
        val level = getItemSpecificEnchantLevel(player.combatStack(e.source), GeneratedEnchantments.ADVANCED_FIRE_MASTER)
        if (level <= 0) return

        // A burning corpse vents its remaining heat onto everything around it.
        val burst = dying.getMaxHealth() * 0.05f * level
        if (burst <= 0f) return
        val box = Box(
            dying.x - BURST_RADIUS, dying.y - BURST_RADIUS, dying.z - BURST_RADIUS,
            dying.x + BURST_RADIUS, dying.y + BURST_RADIUS, dying.z + BURST_RADIUS,
        )
        val nearby = dying.world.getEntitiesByClass(LivingEntity::class.java, box) {
            it.isAlive && it !== dying && it !== player && !it.isTeammate(player)
        }
        var hit = 0
        for (candidate in nearby) {
            if (candidate.damageElemental(player, ElementType.FIRE.damageType, burst)) hit++
        }
        FSTLog.log("AdvancedFireMasterHandler") {
            "player=${player.name.string}, level=$level, burst=$burst, hit=$hit, outcome=burst"
        }
    }
}
