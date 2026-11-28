package dev.firefly.simpletweaks.enchantments.handlers.epic

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
 * `blocking`: restores the pre-1.9 sword block. The block itself comes from the `Item` mixin; this
 * handler halves the damage before armour, matching the old 50% reduction.
 */
@ModEnchantment(
    id = "blocking",
    category = EnchantCategory.EPIC,
    type = EnchantType.SWORD,
    maxLevel = 1,
    weight = EnchantItemWeights.EPIC,
    anvilCost = 8,
    minCostBase = 25,
    minCostPerLevel = 10,
    supportedItems = EnchantSupportedItemTags.SWORD,
    slots = [EnchantSlot.MAINHAND],
    damagePerLevel = 0.0,
    order = 104,
)
object EnchantBlockingHandler : Listenable {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        if (auxDamageInFlight) return
        if (e.source.isIn(DamageTypeTags.BYPASSES_ARMOR)) return
        val player = e.entityLiving as? PlayerEntity ?: return
        if (!SwordBlocking.isBlocking(player)) return
        if (SwordBlocking.advancedLevel(player.activeItem) > 0) return

        val before = e.amount
        e.amount = (e.amount * (1f - SwordBlocking.BASE_DAMAGE_REDUCTION)).coerceAtLeast(0f)
        FSTLog.log("Blocking") {
            "player=${player.name.string}, amount=$before->${e.amount}, outcome=reduced"
        }
    }
}
