package dev.firefly.simpletweaks.enchantments.handlers.uncommon

import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.compat.WorldSide
import dev.firefly.simpletweaks.compat.event.LivingDamageEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.util.getArmorEnchantLevel
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment

/**
 * 1.21 port of `enchantments/handlers/uncommon/EnchantResilienceHandler.kt`.
 *
 * Line-by-line mapping from 1.12.2:
 * | 1.12.2                                       | 1.21.1                                                  |
 * |----------------------------------------------|---------------------------------------------------------|
 * | `net.minecraftforge...LivingDamageEvent`     | `compat.event.LivingDamageEvent`                         |
 * | `victim.world.isRemote`                      | `WorldSide.isClient(victim.world)` (field_9236 is a FIELD) |
 * | `victim.getArmorEnchantLevel(EnchantResilience)` | `victim.getArmorEnchantLevel(GeneratedEnchantments.RESILIENCE)` (already ported in `util/EnchantmentsUtil.kt`) |
 * | `victim.maxHealth` / `victim.health`         | unchanged (`getMaxHealth` method_6063 / `getHealth` method_6032) |
 */
@ModEnchantment(
    id = "resilience",
    category = EnchantCategory.UNCOMMON,
    type = EnchantType.ARMOR,
    maxLevel = 3,
    weight = 8,
    anvilCost = 4,
    minCostBase = 10,
    minCostPerLevel = 10,
    supportedItems = "#minecraft:enchantable/armor",
    slots = [EnchantSlot.ARMOR],
    order = 12,
)
object EnchantResilienceHandler : Listenable {

    @SubscribeEvent
    fun onLivingDamage(event: LivingDamageEvent) {
        if (event.invalid) return

        val victim = event.entityLiving ?: return
        if (WorldSide.isClient(victim.world)) return

        val level = victim.getArmorEnchantLevel(GeneratedEnchantments.RESILIENCE)
        if (level <= 0) return

        val maxHealth = victim.maxHealth
        if (maxHealth <= 0f) return

        val missingRatio = (1f - victim.health / maxHealth).coerceIn(0f, 1f)
        if (missingRatio <= 0f) return

        val reduction = (0.07f * level * missingRatio / 0.9f).coerceAtMost(0.84f)

        event.amount *= (1f - reduction)
        STLog.log("Resilience") {
            "victim=${victim.name.string}, lvl=$level, health=${victim.health}/$maxHealth, " +
                "missingRatio=$missingRatio, reduction=$reduction, damage=${event.amount}"
        }
    }
}
