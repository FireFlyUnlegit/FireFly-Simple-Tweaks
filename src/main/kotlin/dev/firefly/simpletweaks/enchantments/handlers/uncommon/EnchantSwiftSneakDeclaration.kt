package dev.firefly.simpletweaks.enchantments.handlers.uncommon

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.AttributeSpec
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment

/**
 * The `@ModEnchantment` declaration for `swift_sneak`.
 *
 * Like ``prismatic_blessing`` and ``infinite_power``, this enchantment has **no handler object to
 * put the annotation on**: it is purely declarative, so the 1.12.2 handler was dropped rather than
 * ported. This file is the definition — key, index metadata and the tags — and it declares no
 * ``order``, because there are no listeners here to place.
 *
 * The attribute id is derived by the processor as ``simple_tweaks:enchantment.<id>.<attribute tail>``,
 * which differs from the id the hand-written JSON carried (it had no suffix). That was accepted: the id
 * only has to be unique per item, and the derived one is more legible.
 */
@ModEnchantment(
    id = "swift_sneak",
    category = EnchantCategory.UNCOMMON,
    type = EnchantType.LEGGINGS,
    maxLevel = 6,
    weight = 8,
    anvilCost = 4,
    minCostBase = 16,
    minCostPerLevel = 4,
    supportedItems = "#minecraft:enchantable/leg_armor",
    slots = [EnchantSlot.LEGS],
    attributes = [
        AttributeSpec("minecraft:player.sneaking_speed", 0.15, operation = "add_value"),
    ],
)
object EnchantSwiftSneakDeclaration : Listenable
