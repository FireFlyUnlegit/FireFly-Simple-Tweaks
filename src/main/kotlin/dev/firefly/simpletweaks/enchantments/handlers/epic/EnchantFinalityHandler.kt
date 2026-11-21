package dev.firefly.simpletweaks.enchantments.handlers.epic

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.WorldSide
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

/**
 * `finality`: a plain multiplier on the damage this weapon lands, `1%` per level.
 *
 * Applied on `LivingDamageEvent` so it scales whatever the hit already is — melee, arrow, or a rider
 * that the hit spawned. Multiplies, does not replace: co-exists with `arcane_echo`'s final damage.
 */
@ModEnchantment(
    id = "finality",
    category = EnchantCategory.EPIC,
    type = EnchantType.WEAPON,
    maxLevel = 3,
    weight = EnchantItemWeights.EPIC,
    // The 1%/level is applied by the handler on final damage; the derived flat `minecraft:damage`
    // component would add 1.25 attack damage per level on top of it and dwarf the percentage.
    damagePerLevel = 0.0,
    anvilCost = 8,
    minCostBase = 25,
    minCostPerLevel = 10,
    supportedItems = EnchantSupportedItemTags.WEAPON,
    slots = [EnchantSlot.MAINHAND],
    order = 60,
)
object EnchantFinalityHandler : Listenable {

    private const val PER_LEVEL = 0.01

    @SubscribeEvent(priority = 35)
    fun onLivingDamage(e: LivingDamageEvent) {
        if (e.invalid) return
        if (WorldSide.isClient(e.entityLiving.world)) return
        val attacker = e.source.attacker as? PlayerEntity ?: return
        if (attacker === e.entityLiving) return
        val level = getItemSpecificEnchantLevel(attacker.combatStack(e.source), GeneratedEnchantments.FINALITY)
        if (level <= 0) return
        val before = e.amount
        e.amount = (e.amount * (1.0 + PER_LEVEL * level)).toFloat()
        FSTLog.log("Finality") {
            "attacker=${attacker.name.string}, level=$level, amount=$before->${e.amount}, outcome=amplified"
        }
    }
}
