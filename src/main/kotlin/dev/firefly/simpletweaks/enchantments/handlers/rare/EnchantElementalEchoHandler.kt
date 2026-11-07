package dev.firefly.simpletweaks.enchantments.handlers.rare

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.event.LivingDamageEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.*
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.boots
import dev.firefly.simpletweaks.util.elements.ElementAuras
import dev.firefly.simpletweaks.util.elements.ElementType
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.player.PlayerEntity

/**
 * 元素回响 — taking elemental damage attaches a fraction of that element to **yourself**.
 *
 * ## Why this is worth a slot
 * Affinity stacking (`eight affinities` + `elemental_ward`) turns elemental damage into a resource *denial*
 * problem: you spend four slots to take less. This converts the same slots into an **offensive** resource:
 * get hit by fire, then use a fire conversion and the fire is already on you for a vaporize/melt.
 *
 * ## Why the amount is capped at one weak application
 * The aura on yourself is later consumed by *your own* attack, so scaling it with level would let a level III
 * book turn one creeper into a permanently loaded gun. `min(0.3 × level, WEAK)` keeps it at most one normal
 * application.
 */
@ModEnchantment(
    id = "elemental_echo",
    category = EnchantCategory.RARE,
    type = EnchantType.BOOTS,
    maxLevel = 3,
    anvilCost = 5,
    minCostBase = 18,
    supportedItems = EnchantSupportedItemTags.FOOT_ARMOR,
    slots = [EnchantSlot.FEET],
)
object EnchantElementalEchoHandler : Listenable {

    private const val PER_LEVEL = 0.3f

    @SubscribeEvent
    fun onLivingDamage(e: LivingDamageEvent) {
        val player = e.entityLiving as? PlayerEntity ?: return
        if (player.world.isClient) return

        val element = ElementType.of(e.source)
        if (element == ElementType.PHYSICAL) return

        val level = getItemSpecificEnchantLevel(player.boots, GeneratedEnchantments.ELEMENTAL_ECHO)
        if (level <= 0) return

        val units = (PER_LEVEL * level).coerceAtMost(ElementAuras.WEAK)
        ElementAuras.attach(player, element, units)
        FSTLog.log("ElementalEcho") {
            "element=${element.id}, units=${"%.2f".format(units)}, outcome=absorbed"
        }
    }
}
