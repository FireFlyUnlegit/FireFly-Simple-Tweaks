package dev.firefly.simpletweaks.enchantments.handlers.mystery

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.*

/**
 * 元素主宰 — the conversion element is chosen to **react with what the target already carries**, and every
 * reaction you trigger is worth more.
 *
 * ## What it breaks
 * Every conversion in this mod is a fixed element: you hold `fire_conversion` and you get fire, every hit,
 * forever. This is the only enchantment that makes the conversion element **dynamic**, which turns the
 * reaction table from a lookup you plan around into something you can steer mid-fight: hold one
 * conversion and you carry all seven that matter, as long as an aura is present.
 *
 * ## Where the effect lives
 * - element selection: `ElementCombat.conversionOf(attacker, stack, target)` — it needs the target, which is
 *   why that signature gained a parameter in `cleanup38`;
 * - reaction bonus: `ElementMarks.sovereignLevel` scales both amplifying and transformative outcomes by
 *   `1 + 0.30 × level`.
 *
 * ⚠️ It deliberately does **not** double the conversion rate: that would duplicate `elemental_mastery`
 * (+10%/level elemental damage, cap +200%) and make the two impossible to balance against each other.
 *
 * ⚠️ With no aura on the target there is nothing to react with, so it falls back to your own conversion
 * element — a random pick would make the enchantment feel broken rather than clever.
 */
@ModEnchantment(
    id = "elemental_sovereign",
    category = EnchantCategory.MYSTERY,
    type = EnchantType.SWORD,
    maxLevel = 3,
    weight = EnchantItemWeights.MYSTERY,
    anvilCost = 90,
    minCostBase = 30,
    minCostPerLevel = 30,
    supportedItems = EnchantSupportedItemTags.SWORD,
    slots = [EnchantSlot.MAINHAND],
    damagePerLevel = 2.5,
    order = 53,
)
object EnchantElementalSovereignHandler : Listenable
