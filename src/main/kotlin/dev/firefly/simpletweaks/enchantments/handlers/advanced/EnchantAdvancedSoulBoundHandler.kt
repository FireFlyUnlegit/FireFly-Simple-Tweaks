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
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.player.PlayerEntity
/** The advanced form of `soul_bound`: its top tier plus a further share of damage taken. */
@ModEnchantment(
    id = "advanced_soul_bound",
    category = EnchantCategory.ADVANCED_LEGENDARY,
    type = EnchantType.ARMOR,
    maxLevel = 3,
    weight = EnchantItemWeights.ADVANCED_LEGENDARY,
    anvilCost = 12,
    minCostBase = 35,
    minCostPerLevel = 15,
    supportedItems = EnchantSupportedItemTags.ARMOR,
    slots = [EnchantSlot.ARMOR],
    order = 90,
)
object EnchantAdvancedSoulBoundHandler : Listenable {

    private const val BASE_LEVEL = 1
    private const val PER_LEVEL = 0.03f

    @SubscribeEvent
    fun onLivingDamage(e: LivingDamageEvent) {
        if (e.invalid) return
        val player = e.entityLiving as? PlayerEntity ?: return
        var level = 0
        for (slot in EquipmentSlot.entries) {
            if (slot.type != EquipmentSlot.Type.HUMANOID_ARMOR) continue
            level += getItemSpecificEnchantLevel(player.getEquippedStack(slot), GeneratedEnchantments.ADVANCED_SOUL_BOUND)
        }
        if (level <= 0) return
        val before = e.amount
        e.amount *= (1f - PER_LEVEL * (BASE_LEVEL + level)).coerceAtLeast(0f)
        FSTLog.log("AdvancedSoulBoundHandler") { "player=${player.name.string}, level=$level, damage=$before->${e.amount}, outcome=reduced" }
    }
}