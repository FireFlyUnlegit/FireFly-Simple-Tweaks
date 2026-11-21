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
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.player.PlayerEntity

/** The advanced form of `finality`: the III bonus plus a little more against untouched targets. */
@ModEnchantment(
    id = "advanced_finality",
    category = EnchantCategory.ADVANCED_EPIC,
    type = EnchantType.WEAPON,
    maxLevel = 3,
    weight = EnchantItemWeights.ADVANCED_EPIC,
    damagePerLevel = 0.0,
    anvilCost = 12,
    minCostBase = 35,
    minCostPerLevel = 15,
    supportedItems = EnchantSupportedItemTags.WEAPON,
    slots = [EnchantSlot.MAINHAND],
    order = 77,
)
object EnchantAdvancedFinalityHandler : Listenable {

    private const val BASE_LEVEL = 3
    private const val PER_LEVEL = 0.01f
    private const val FULL_HEALTH_PER_LEVEL = 0.02f

    @SubscribeEvent
    fun onLivingDamage(e: LivingDamageEvent) {
        if (e.invalid) return
        val player = e.attacker as? PlayerEntity ?: return
        val victim = e.entityLiving
        if (victim === player) return

        val level = getItemSpecificEnchantLevel(player.combatStack(e.source), GeneratedEnchantments.ADVANCED_FINALITY)
        if (level <= 0) return

        val before = e.amount
        e.amount *= 1f + PER_LEVEL * (BASE_LEVEL + level)
        if (victim.health >= victim.maxHealth) e.amount *= 1f + FULL_HEALTH_PER_LEVEL * level
        FSTLog.log("AdvancedFinality") {
            "player=${player.name.string}, level=$level, damage=$before->${e.amount}, outcome=amplified"
        }
    }
}
