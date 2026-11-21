package dev.firefly.simpletweaks.enchantments.handlers.epic

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantItemWeights
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSupportedItemTags
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment

/**
 * `reaction_surge`: `1%` per level on reaction damage.
 *
 * Declarative only — the multiplier is read by `ElementMarks.reactionMultiplier`, because reaction
 * damage is computed inside the element engine rather than raised as an event a handler could see.
 */
@ModEnchantment(
    id = "reaction_surge",
    category = EnchantCategory.EPIC,
    type = EnchantType.WEAPON,
    maxLevel = 3,
    weight = EnchantItemWeights.EPIC,
    damagePerLevel = 0.0,
    anvilCost = 8,
    minCostBase = 25,
    minCostPerLevel = 10,
    supportedItems = EnchantSupportedItemTags.WEAPON,
    slots = [EnchantSlot.MAINHAND],
    order = 62,
)
object EnchantReactionSurgeHandler : Listenable
