package dev.firefly.simpletweaks.enchantments.handlers.legendary

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
import dev.firefly.simpletweaks.util.getArmorEnchantLevel
import dev.firefly.simpletweaks.util.syncAttributes
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.Identifier

/**
 * 旺盛生机 — the legendary step above `vitality`, which is EPIC and capped at 10.
 *
 * `t` is the total level across the four armour slots. The same stair-step shape as Vitality, with
 * taller steps: `8t + 25⌊t/10⌋`, then `×(1 + 0.015t)`. At `t = 40` (four pieces, ten levels each) that
 * is `+672` max health against Vitality's `+280`; the two stack, because they are different modifiers.
 *
 * The tick compares the modifier already on the entity instead of caching the applied level, so it is
 * allocation-free once the value is right (see the spark note in `EnchantVitalityHandler`).
 */
@ModEnchantment(
    id = "greater_vitality",
    category = EnchantCategory.LEGENDARY,
    type = EnchantType.ARMOR,
    maxLevel = 10,
    weight = EnchantItemWeights.LEGENDARY,
    anvilCost = 10,
    minCostBase = 20,
    minCostPerLevel = 15,
    supportedItems = EnchantSupportedItemTags.ARMOR,
    slots = [EnchantSlot.ARMOR],
    order = 65,
)
object EnchantGreaterVitalityHandler : Listenable {

    private val MODIFIER_ID: Identifier = Identifier.of(SimpleTweaks.MOD_ID, "greater_vitality")

    /** Base max-health amount for [total] levels, before the second-order term. */
    @JvmStatic
    fun bonusFor(total: Int): Double = total * 8.0 + (total / 10) * 25.0

    /** The `×(1 + 0.015t)` term. */
    @JvmStatic
    fun scaleFor(total: Int): Double = 1.0 + 0.015 * total

    /** What the modifier holds for [total] levels. */
    @JvmStatic
    fun modifierValue(total: Int): Double = bonusFor(total) * scaleFor(total)

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        val p = e.player
        if (WorldSide.isClient(p.world)) return

        val attr = p.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH) ?: return
        val total = p.getArmorEnchantLevel(GeneratedEnchantments.GREATER_VITALITY)
        val hasModifier = attr.getModifier(MODIFIER_ID) != null

        if (total <= 0 && !hasModifier) return
        if (total > 0 && hasModifier && attr.getModifier(MODIFIER_ID)?.value == modifierValue(total)) return

        attr.removeModifier(MODIFIER_ID)
        if (total > 0) {
            attr.addPersistentModifier(
                EntityAttributeModifier(MODIFIER_ID, modifierValue(total), EntityAttributeModifier.Operation.ADD_VALUE)
            )
            FSTLog.log("GreaterVitality") {
                "player=${p.name.string}, totalLvl=$total, bonus=${bonusFor(total)}, " +
                    "modifier=${modifierValue(total)}, outcome=applied"
            }
        } else {
            FSTLog.log("GreaterVitality") {
                "player=${p.name.string}, totalLvl=$total, outcome=removed"
            }
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
}
