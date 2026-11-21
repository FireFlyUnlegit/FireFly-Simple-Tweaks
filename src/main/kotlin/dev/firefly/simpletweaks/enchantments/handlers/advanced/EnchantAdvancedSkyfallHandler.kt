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

/** The advanced form of this enchantment: its top tier plus an effect of its own. */
@ModEnchantment(
    id = "advanced_skyfall",
    category = EnchantCategory.ADVANCED_LEGENDARY,
    type = EnchantType.WEAPON,
    maxLevel = 4,
    weight = EnchantItemWeights.ADVANCED_LEGENDARY,
    damagePerLevel = 0.0,
    anvilCost = 16,
    minCostBase = 40,
    minCostPerLevel = 20,
    supportedItems = EnchantSupportedItemTags.WEAPON,
    slots = [EnchantSlot.MAINHAND],
    order = 93,
)
object EnchantAdvancedSkyfallHandler : Listenable {

    private const val FALL_CAP = 6f

    @SubscribeEvent
    fun onLivingDamage(e: LivingDamageEvent) {
        if (e.invalid) return
        val player = e.attacker as? PlayerEntity ?: return
        val victim = e.entityLiving
        if (victim === player) return
        val level = getItemSpecificEnchantLevel(player.combatStack(e.source), GeneratedEnchantments.ADVANCED_SKYFALL)
        if (level <= 0) return
        if (player.isOnGround) return

        // Gravity is the weapon: the longer the drop, the harder the landing (capped so a sky-limit
        // jump does not turn into an instant kill).
        val before = e.amount
        val drop = player.fallDistance.coerceIn(0f, FALL_CAP)
        e.amount *= 1f + 0.06f * level * (1f + drop)
        FSTLog.log("AdvancedSkyfallHandler") {
            "player=${player.name.string}, level=$level, drop=$drop, damage=$before->${e.amount}, outcome=plunged"
        }
    }
}
