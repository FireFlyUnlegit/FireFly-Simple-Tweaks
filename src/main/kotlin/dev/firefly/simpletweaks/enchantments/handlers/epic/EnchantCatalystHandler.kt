package dev.firefly.simpletweaks.enchantments.handlers.epic

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.*

/**
 * 元素催化 — shortens the **attachment ICD** by 0.5 s per level (2.5 s → 2.0 / 1.5 / 1.0 s).
 *
 * ## Why a declaration with no handler body
 * The effect lives where the ICD is consulted (`ElementMarks.catalystLevel`), because the window is engine
 * state and the counter must stay in one place. Putting a second counter in a handler would let the two
 * disagree, which is exactly the "键存在 ≠ 有人读它" failure this repository keeps hitting.
 *
 * ⚠️ The class is still a [Listenable] so the KSP processor emits its datapack JSON and registry key; it
 * simply has no `@SubscribeEvent` method to register.
 */
@ModEnchantment(
    id = "elemental_catalyst",
    category = EnchantCategory.EPIC,
    type = EnchantType.CHESTPLATE,
    maxLevel = 3,
    anvilCost = 8,
    minCostBase = 25,
    supportedItems = EnchantSupportedItemTags.CHEST_ARMOR,
    slots = [EnchantSlot.CHEST],
)
object EnchantCatalystHandler : Listenable
