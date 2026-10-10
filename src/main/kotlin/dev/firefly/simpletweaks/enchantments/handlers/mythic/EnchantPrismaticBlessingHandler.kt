package dev.firefly.simpletweaks.enchantments.handlers.mythic

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.AttributeSpec
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment

/**
 * `prismatic_blessing`: `+4%` per level to a long list of attributes.
 *
 * ## Why this object has no event handlers
 * That is not an oversight — the enchantment is **purely declarative**. Its entire effect is the
 * `minecraft:attributes` block below, which vanilla applies by itself, so the 1.12.2 handler was dropped
 * rather than ported (`MIGRATION.md` §10.2). It still needs an `@ModEnchantment` carrier, because "one file
 * is the whole definition" is what the annotation is for: this file *is* the definition now.
 *
 * ## Why the attribute list is spelled out here
 * It is the list the hand-written `prismatic_blessing.json` carried, in the same order. Worth knowing:
 * that file had been edited by hand up to 17 attributes, while `$declarativeEffects` in
 * `tools/gen-enchantments.ps1` still only listed **6** — so any re-run of that script would have silently
 * dropped 11 of them. Migrating the definition here removes that hazard, and the script now skips this
 * enchantment automatically because it parses `@ModEnchantment`.
 */
@ModEnchantment(
    id = "prismatic_blessing",
    category = EnchantCategory.MYTHIC,
    type = EnchantType.BREAKABLE,
    maxLevel = 5,
    weight = 1,
    anvilCost = 12,
    minCostBase = 45,
    minCostPerLevel = 15,
    supportedItems = "#minecraft:enchantable/durability",
    slots = [EnchantSlot.ANY],
    attributes = [
        AttributeSpec("minecraft:generic.max_health", 0.04),
        AttributeSpec("minecraft:generic.movement_speed", 0.04),
        AttributeSpec("minecraft:generic.attack_damage", 0.04),
        AttributeSpec("minecraft:generic.attack_speed", 0.04),
        AttributeSpec("minecraft:generic.armor", 0.04),
        AttributeSpec("minecraft:generic.armor_toughness", 0.04),
        AttributeSpec("minecraft:generic.knockback_resistance", 0.04),
        AttributeSpec("minecraft:generic.attack_knockback", 0.04),
        AttributeSpec("minecraft:generic.flying_speed", 0.04),
        AttributeSpec("minecraft:generic.explosion_knockback_resistance", 0.04),
        AttributeSpec("minecraft:generic.water_movement_efficiency", 0.04),
        AttributeSpec("minecraft:generic.luck", 0.04),
        AttributeSpec("minecraft:generic.safe_fall_distance", 0.04),
        AttributeSpec("minecraft:player.block_break_speed", 0.04),
        AttributeSpec("minecraft:player.mining_efficiency", 0.04),
        AttributeSpec("minecraft:player.submerged_mining_speed", 0.04),
        AttributeSpec("minecraft:player.sweeping_damage_ratio", 0.04),
    ],
)
object EnchantPrismaticBlessingHandler : Listenable
