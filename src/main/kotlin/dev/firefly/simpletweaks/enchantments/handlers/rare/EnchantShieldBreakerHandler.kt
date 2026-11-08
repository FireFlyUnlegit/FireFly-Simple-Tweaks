package dev.firefly.simpletweaks.enchantments.handlers.rare

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.*
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.combatStack
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.AxeItem

/**
 * 破盾 — an axe hit on a **blocking** target knocks the shield out of use and shreds it.
 *
 * ## Why this is a real mechanic and not a debuff
 * Vanilla has no answer to a raised shield except "hit it until it breaks" or "use an axe to disable it for
 * 5 s" — the axe behaviour exists, but only as the `shieldDisable` path on a successful block, which a
 * damage handler cannot observe. So this reads the state instead of the event: if the target is blocking at
 * the moment of the hit, we drop the block (`clearActiveItem`) and damage the shield.
 *
 * ## Numbers
 * - shield durability damage: `2 + level` extra durability
 * - block is cleared immediately, so the same swing also lands for full damage
 *
 * ## Guard
 * Only axes count (`AxeItem`), matching vanilla's fantasy — a sword with this enchantment does nothing, and
 * that is deliberate: it keeps the counter-play readable.
 */
@ModEnchantment(
    id = "shield_breaker",
    category = EnchantCategory.RARE,
    type = EnchantType.TOOL,
    maxLevel = 3,
    anvilCost = 6,
    minCostBase = 20,
    supportedItems = EnchantSupportedItemTags.AXE,
    slots = [EnchantSlot.MAINHAND],
)
object EnchantShieldBreakerHandler : Listenable {

    private const val EXTRA_DURABILITY_PER_LEVEL = 2

    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        val attacker = e.source.attacker as? PlayerEntity ?: return
        val target = e.entityLiving
        if (attacker === target) return
        if (!target.isBlocking) return

        val weapon = attacker.combatStack(e.source)
        if (weapon.item !is AxeItem) return

        val level = getItemSpecificEnchantLevel(weapon, GeneratedEnchantments.SHIELD_BREAKER)
        if (level <= 0) return

        val shield = target.activeItem
        if (shield.isEmpty) return

        // Drop the block first: the target then takes this same hit unprotected.
        target.clearActiveItem()
        shield.damage(
            EXTRA_DURABILITY_PER_LEVEL * level,
            target,
            if (target.activeHand == net.minecraft.util.Hand.MAIN_HAND) EquipmentSlot.MAINHAND else EquipmentSlot.OFFHAND,
        )

        FSTLog.log("ShieldBreaker") {
            "target=${target.type.name.string}, level=$level, shield=${shield.item}, " +
                "durability=${shield.maxDamage - shield.damage}, outcome=broken"
        }
    }
}
