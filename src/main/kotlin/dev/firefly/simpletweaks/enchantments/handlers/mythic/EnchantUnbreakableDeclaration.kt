package dev.firefly.simpletweaks.enchantments.handlers.mythic

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantItemWeights
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSupportedItemTags
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment

/**
 * The `@ModEnchantment` declaration for `unbreakable`.
 *
 * Like ``prismatic_blessing`` and ``infinite_power``, this enchantment has **no handler object to
 * put the annotation on**: it is purely declarative, so the 1.12.2 handler was dropped rather than
 * ported. This file is the definition — key, index metadata and the tags — and it declares no
 * ``order``, because there are no listeners here to place.
 *
 * ``jsonEmit = false``: its ``effects`` use ``minecraft:item_damage`` with ``set 0``, which this processor
 * cannot emit, so the hand-written ``$id.json`` stays authoritative for the datapack definition.
 * The fields below document what that file contains — change one, change the other.
 */
@ModEnchantment(
    id = "unbreakable",
    category = EnchantCategory.MYTHIC,
    type = EnchantType.BREAKABLE,
    maxLevel = 1,
    weight = EnchantItemWeights.MYTHIC,
    anvilCost = 12,
    minCostBase = 60,
    supportedItems = EnchantSupportedItemTags.DURABILITY,
    slots = [EnchantSlot.ANY],
    jsonEmit = false,
)
object EnchantUnbreakableDeclaration : Listenable
