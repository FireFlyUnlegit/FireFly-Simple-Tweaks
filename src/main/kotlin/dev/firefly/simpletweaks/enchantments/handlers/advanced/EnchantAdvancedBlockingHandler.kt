package dev.firefly.simpletweaks.enchantments.handlers.advanced

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.event.EventPriority
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
import dev.firefly.simpletweaks.util.SwordBlocking
import dev.firefly.simpletweaks.util.damagesource.auxDamageInFlight
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.registry.tag.DamageTypeTags

/**
 * The advanced form of `blocking`: its own level replaces the base half, adding 8% damage reduction
 * and 18% knockback reduction per level.
 */
@ModEnchantment(
    id = "advanced_blocking",
    category = EnchantCategory.ADVANCED_EPIC,
    type = EnchantType.SWORD,
    maxLevel = 5,
    ladderBase = 1,
    weight = EnchantItemWeights.ADVANCED_EPIC,
    anvilCost = 12,
    minCostBase = 35,
    minCostPerLevel = 15,
    supportedItems = EnchantSupportedItemTags.SWORD,
    slots = [EnchantSlot.MAINHAND],
    damagePerLevel = 0.0,
    order = 105,
)
object EnchantAdvancedBlockingHandler : Listenable {

    @SubscribeEvent(priority = EventPriority.HIGHEST + 10)
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        if (auxDamageInFlight) return
        if (e.source.isIn(DamageTypeTags.BYPASSES_ARMOR)) return
        val player = e.entityLiving as? PlayerEntity ?: return
        if (!SwordBlocking.isBlocking(player)) return
        val level = SwordBlocking.advancedLevel(player.activeItem)
        if (level <= 0) return

        val before = e.amount
        e.amount = (e.amount * (1f - SwordBlocking.damageReduction(player))).coerceAtLeast(0f)
        FSTLog.log("AdvancedBlocking") {
            "player=${player.name.string}, level=$level, amount=$before->${e.amount}, outcome=reduced"
        }
    }
}
