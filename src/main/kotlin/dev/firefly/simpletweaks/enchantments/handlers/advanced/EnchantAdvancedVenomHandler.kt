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
import dev.firefly.simpletweaks.util.elements.ElementAuras
import dev.firefly.simpletweaks.util.elements.ElementType
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.Box

/** The advanced form of this enchantment: its top tier plus an effect of its own. */
@ModEnchantment(
    id = "advanced_venom",
    category = EnchantCategory.ADVANCED_RARE,
    type = EnchantType.SWORD,
    maxLevel = 4,
    ladderBase = 3,
    weight = EnchantItemWeights.ADVANCED_RARE,
    damagePerLevel = 0.0,
    anvilCost = 14,
    minCostBase = 35,
    minCostPerLevel = 15,
    supportedItems = EnchantSupportedItemTags.SWORD,
    slots = [EnchantSlot.MAINHAND],
    order = 75,
)
object EnchantAdvancedVenomHandler : Listenable {

    private const val CLOUD_RADIUS = 4.0

    @SubscribeEvent
    fun onLivingHurt(e: dev.firefly.simpletweaks.compat.event.LivingHurtEvent) {
        if (e.invalid) return
        val player = e.attacker as? PlayerEntity ?: return
        val victim = e.entityLiving
        if (victim === player) return
        val level = getItemSpecificEnchantLevel(player.combatStack(e.source), GeneratedEnchantments.ADVANCED_VENOM)
        if (level <= 0) return
        val poisoned = victim.getStatusEffect(net.minecraft.entity.effect.StatusEffects.POISON) != null
        victim.addStatusEffect(
            net.minecraft.entity.effect.StatusEffectInstance(
                net.minecraft.entity.effect.StatusEffects.POISON,
                40 * (3 + level),
                0,
            ),
        )
        if (poisoned) {
            ElementAuras.attach(victim, ElementType.NATURE, ElementAuras.STRONG)
        }
        FSTLog.log("AdvancedVenomHandler") {
            "player=${player.name.string}, level=$level, poisoned=$poisoned, outcome=injected"
        }
    }

    @SubscribeEvent
    fun onLivingDeath(e: dev.firefly.simpletweaks.compat.event.LivingDeathEvent) {
        val dying = e.entityLiving
        val player = e.source?.attacker as? PlayerEntity ?: return
        val level = getItemSpecificEnchantLevel(player.combatStack(e.source), GeneratedEnchantments.ADVANCED_VENOM)
        if (level <= 0) return
        if (dying.getStatusEffect(net.minecraft.entity.effect.StatusEffects.POISON) == null) return

        // A poisoned corpse bursts: everything in reach breathes it in.
        val box = Box(
            dying.x - CLOUD_RADIUS, dying.y - CLOUD_RADIUS, dying.z - CLOUD_RADIUS,
            dying.x + CLOUD_RADIUS, dying.y + CLOUD_RADIUS, dying.z + CLOUD_RADIUS,
        )
        val nearby = dying.world.getEntitiesByClass(LivingEntity::class.java, box) {
            it.isAlive && it !== dying && it !== player && !it.isTeammate(player)
        }
        var hit = 0
        for (candidate in nearby) {
            candidate.addStatusEffect(
                net.minecraft.entity.effect.StatusEffectInstance(
                    net.minecraft.entity.effect.StatusEffects.POISON,
                    20 * (3 + level),
                    0,
                ),
            )
            ElementAuras.attach(candidate, ElementType.NATURE, ElementAuras.WEAK)
            hit++
        }
        FSTLog.log("AdvancedVenomHandler") {
            "player=${player.name.string}, level=$level, hit=$hit, outcome=cloud"
        }
    }
}
