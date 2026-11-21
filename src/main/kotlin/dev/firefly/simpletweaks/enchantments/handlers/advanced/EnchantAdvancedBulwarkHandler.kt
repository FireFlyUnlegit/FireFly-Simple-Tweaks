package dev.firefly.simpletweaks.enchantments.handlers.advanced

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.event.LivingDamageEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.util.damagesource.isAuxiliaryDamage
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantItemWeights
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSupportedItemTags
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.TempAbsorption
import dev.firefly.simpletweaks.util.UpgradeEnchantments
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.player.PlayerEntity

/** The advanced form of `bulwark`: the III reduction, and a share of what it saves returns as health. */
@ModEnchantment(
    id = "advanced_bulwark",
    category = EnchantCategory.ADVANCED_EPIC,
    type = EnchantType.ARMOR,
    maxLevel = 4,
    ladderBase = 3,
    weight = EnchantItemWeights.ADVANCED_EPIC,
    anvilCost = 12,
    minCostBase = 35,
    minCostPerLevel = 15,
    supportedItems = EnchantSupportedItemTags.ARMOR,
    slots = [EnchantSlot.ARMOR],
    order = 79,
)
object EnchantAdvancedBulwarkHandler : Listenable {

    private const val PER_LEVEL = 0.01f
    private const val HEAL_SHARE_PER_LEVEL = 0.005f

    @SubscribeEvent
    fun onLivingDamage(e: LivingDamageEvent) {
        if (e.invalid) return
        if (e.source.isAuxiliaryDamage()) return
        val player = e.entityLiving as? PlayerEntity ?: return

        var level = 0
        for (slot in EquipmentSlot.entries) {
            if (slot.type != EquipmentSlot.Type.HUMANOID_ARMOR) continue
            level += getItemSpecificEnchantLevel(player.getEquippedStack(slot), GeneratedEnchantments.ADVANCED_BULWARK)
        }
        if (level <= 0) return

        val before = e.amount
        e.amount *= (1f - PER_LEVEL * level).coerceIn(0.25f, 1f)
        val saved = before - e.amount
        if (saved > 0f) TempAbsorption.grant(player, saved * 0.5f, level.coerceIn(1, 2))
        FSTLog.log("AdvancedBulwark") {
            "player=${player.name.string}, level=$level, damage=$before->${e.amount}, outcome=reduced"
        }
    }
}
