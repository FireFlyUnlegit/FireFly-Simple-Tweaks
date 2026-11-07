package dev.firefly.simpletweaks.enchantments.handlers.mystery

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.*

/**
 * 万法归元 — your **highest** elemental affinity defends against **all eight** elements.
 *
 * ## The rule it breaks
 * The eight affinities were designed as "mutually compatible but horizontal": four pieces of fire protection
 * make you fire-proof and nothing else. This enchantment inverts the maths — the engine takes the largest
 * single-element total and uses it for every element, so a specialist gains universal coverage.
 *
 * ## Why it cannot stack into a monster
 * The answer is a **maximum**, not a sum: one piece of each element still yields the single highest total,
 * and adding a supremacy book does not add levels, it only re-points them. Four pieces of X (say 4 × 5 = 20
 * levels, i.e. the 80 % cap) therefore cost the same four slots a generalist would spend for a fraction of
 * that.
 *
 * ## Where the effect lives
 * `ElementCombat.protectionLevel` — the only reader of protective levels. Putting the rule anywhere else would
 * let two call sites disagree about how much resistance a hit should have seen.
 */
@ModEnchantment(
    id = "elemental_supremacy",
    category = EnchantCategory.MYSTERY,
    type = EnchantType.ARMOR,
    maxLevel = 1,
    weight = EnchantItemWeights.MYSTERY,
    anvilCost = 90,
    minCostBase = 30,
    minCostPerLevel = 30,
    supportedItems = EnchantSupportedItemTags.ARMOR,
    slots = [EnchantSlot.ARMOR],
)
object EnchantElementalSupremacyHandler : Listenable
