package dev.firefly.simpletweaks.enchantments.handlers.advanced

import dev.firefly.simpletweaks.compat.FSTLog
import java.util.UUID
import net.minecraft.entity.LivingEntity
import dev.firefly.simpletweaks.util.damagesource.damageElemental
import dev.firefly.simpletweaks.util.damagesource.ModDamageTypes
import dev.firefly.simpletweaks.compat.attacker
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
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

/** The advanced form of `execute`: the V finisher bonus, expressed against the victim's missing health. */
@ModEnchantment(
    id = "advanced_execute",
    category = EnchantCategory.ADVANCED_RARE,
    type = EnchantType.SWORD,
    maxLevel = 3,
    weight = EnchantItemWeights.ADVANCED_RARE,
    anvilCost = 10,
    minCostBase = 35,
    minCostPerLevel = 15,
    supportedItems = EnchantSupportedItemTags.SWORD,
    slots = [EnchantSlot.MAINHAND],
    order = 80,
)
object EnchantAdvancedExecuteHandler : Listenable {

    private const val BASE_LEVEL = 5
    private const val PER_LEVEL = 0.01f

    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        val player = e.attacker as? PlayerEntity ?: return
        val victim = e.entityLiving
        if (victim === player) return

        val level = getItemSpecificEnchantLevel(player.combatStack(e.source), GeneratedEnchantments.ADVANCED_EXECUTE)
        if (level <= 0) return

        val lost = victim.maxHealth - victim.health
        if (lost <= 0f) return

        val before = e.amount
        if (victim.health / victim.maxHealth <= 0.3f) {
            // A finished target is past the point of armour: the whole hit is dealt as true damage.
            e.amount = 0f
            victim.damageElemental(player, ModDamageTypes.TRUE_DAMAGE, before * (1f + 0.15f * level))
        } else {
            e.amount += lost * PER_LEVEL * (BASE_LEVEL + level)
        }
        FSTLog.log("AdvancedExecute") {
            "player=${player.name.string}, level=$level, lost=$lost, damage=$before->${e.amount}, outcome=finished"
        }
    }
}
