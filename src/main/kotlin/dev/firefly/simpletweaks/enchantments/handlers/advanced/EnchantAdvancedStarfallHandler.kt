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
import net.minecraft.entity.player.PlayerEntity

/** The advanced form of this enchantment: its top tier plus an effect of its own. */
@ModEnchantment(
    id = "advanced_starfall",
    category = EnchantCategory.ADVANCED_MYTHIC,
    type = EnchantType.BOW,
    maxLevel = 5,
    weight = EnchantItemWeights.ADVANCED_MYTHIC,
    damagePerLevel = 0.0,
    anvilCost = 18,
    minCostBase = 45,
    minCostPerLevel = 20,
    supportedItems = EnchantSupportedItemTags.BOW_OR_CROSSBOW,
    slots = [EnchantSlot.MAINHAND],
    order = 98,
)
object EnchantAdvancedStarfallHandler : Listenable {

    @SubscribeEvent
    fun onLivingDamage(e: LivingDamageEvent) {
        if (e.invalid) return
        val player = e.attacker as? PlayerEntity ?: return
        val victim = e.entityLiving
        if (victim === player) return
        val level = getItemSpecificEnchantLevel(player.combatStack(e.source), GeneratedEnchantments.ADVANCED_STARFALL)
        if (level <= 0) return

        // Every arrow leaves a star nailed to the target; a second arrow on a lit target makes it burst.
        val lit = ElementAuras.peek(victim)?.first == ElementType.LIGHT
        ElementAuras.attach(victim, ElementType.LIGHT, ElementAuras.STRONG)
        if (!lit) {
            FSTLog.log("AdvancedStarfallHandler") {
                "player=${player.name.string}, level=$level, outcome=marked"
            }
            return
        }

        val burst = e.amount * 0.05f * level
        val landed = victim.damageElemental(player, ModDamageTypes.STAR, burst)
        FSTLog.log("AdvancedStarfallHandler") {
            "player=${player.name.string}, level=$level, burst=$burst, landed=$landed, outcome=burst"
        }
    }
}
