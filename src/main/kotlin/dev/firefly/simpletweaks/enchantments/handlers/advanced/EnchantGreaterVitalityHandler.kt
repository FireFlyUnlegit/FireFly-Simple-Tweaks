package dev.firefly.simpletweaks.enchantments.handlers.advanced

import dev.firefly.simpletweaks.SimpleTweaks
import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.WorldSide
import dev.firefly.simpletweaks.compat.event.PlayerEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.event.TickEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantItemWeights
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSupportedItemTags
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.UpgradeEnchantments
import dev.firefly.simpletweaks.util.syncAttributes
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.Identifier

/**
 * 旺盛生机 — the advanced step above `vitality`.
 *
 * It is not a second source of the same bonus: it *replaces* Vitality at level X (see
 * [UpgradeEnchantments] for how the anvil enforces that). A piece carrying `greater_vitality I` counts
 * as eleven levels on the ladder — the ten of the Vitality it consumed plus its own one — which is also
 * what Arcane Echo sees.
 */
@ModEnchantment(
    id = "greater_vitality",
    category = EnchantCategory.UPGRADED_EPIC,
    type = EnchantType.ARMOR,
    maxLevel = 10,
    weight = EnchantItemWeights.UPGRADED_EPIC,
    anvilCost = 10,
    minCostBase = 20,
    minCostPerLevel = 15,
    supportedItems = EnchantSupportedItemTags.ARMOR,
    slots = [EnchantSlot.ARMOR],
    order = 65,
)
object EnchantGreaterVitalityHandler : Listenable {

    private val MODIFIER_ID: Identifier = Identifier.of(SimpleTweaks.MOD_ID, "greater_vitality")
    private val RULE = UpgradeEnchantments.rules().first { it.upgrade == GeneratedEnchantments.GREATER_VITALITY }

    /** Base max-health amount for [total] ladder levels, before the second-order term. */
    @JvmStatic
    fun bonusFor(total: Int): Double = total * 8.0 + (total / 10) * 25.0

    /** The `×(1 + 0.015t)` term. */
    @JvmStatic
    fun scaleFor(total: Int): Double = 1.0 + 0.015 * total

    /** What the modifier holds for [total] ladder levels. */
    @JvmStatic
    fun modifierValue(total: Int): Double = bonusFor(total) * scaleFor(total)

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        val p = e.player
        if (WorldSide.isClient(p.world)) return

        val attr = p.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH) ?: return
        val total = ladderTotal(p)
        val hasModifier = attr.getModifier(MODIFIER_ID) != null

        if (total <= 0 && !hasModifier) return
        if (total > 0 && hasModifier && attr.getModifier(MODIFIER_ID)?.value == modifierValue(total)) return

        attr.removeModifier(MODIFIER_ID)
        if (total > 0) {
            attr.addPersistentModifier(
                EntityAttributeModifier(MODIFIER_ID, modifierValue(total), EntityAttributeModifier.Operation.ADD_VALUE)
            )
            FSTLog.log("GreaterVitality") {
                "player=${p.name.string}, ladderLvl=$total, bonus=${bonusFor(total)}, " +
                    "modifier=${modifierValue(total)}, outcome=applied"
            }
        } else {
            FSTLog.log("GreaterVitality") { "player=${p.name.string}, ladderLvl=0, outcome=removed" }
        }
        p.syncAttributes()

        if (p.health > p.maxHealth) p.health = p.maxHealth
    }

    @SubscribeEvent
    fun onPlayerClone(event: PlayerEvent.Clone) {
        clearModifier(event.original)
        clearModifier(event.player)
    }

    @SubscribeEvent
    fun onLogout(e: PlayerEvent.PlayerLoggedOutEvent) {
        clearModifier(e.player)
    }

    private fun clearModifier(p: PlayerEntity) {
        p.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH)?.removeModifier(MODIFIER_ID)
    }

    /** Sum of the four armour pieces on the upgrade ladder. */
    private fun ladderTotal(p: PlayerEntity): Int {
        var total = 0
        for (slot in EquipmentSlot.entries) {
            if (slot.type != EquipmentSlot.Type.HUMANOID_ARMOR) continue
            total += UpgradeEnchantments.ladderLevel(p.getEquippedStack(slot), RULE)
        }
        return total
    }
}
