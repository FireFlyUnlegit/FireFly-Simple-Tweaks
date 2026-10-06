package dev.firefly.simpletweaks.enchantments.annotations

/**
 * Vanilla `enchantable` item-tag ids.
 *
 * Kept as `const val` rather than an enum: the set is **open** — a pack or another mod may add its
 * own tag (`#othermod:custom_weapons`), and the annotation must still accept a raw string. Constants
 * give IDE completion and spell-checking for the common cases without closing the door on the rest.
 *
 * **`slots` must correspond** to the tag chosen here, e.g. [ARMOR] with `EnchantSlot.ARMOR`,
 * [HEAD_ARMOR] with `EnchantSlot.HEAD`. A mismatch compiles but the effect never fires.
*/
object EnchantSupportedItemTags {

// weapons
const val SWORD         = "#minecraft:enchantable/sword"
const val SHARP_WEAPON  = "#minecraft:enchantable/sharp_weapon"
const val WEAPON        = "#minecraft:enchantable/weapon"
const val AXE           = "#minecraft:enchantable/axe"
const val SMASHING      = "#minecraft:enchantable/smashing"
const val FIRE_ASPECT   = "#minecraft:enchantable/fire_aspect"

// ranged
const val BOW           = "#minecraft:enchantable/bow"
const val CROSSBOW      = "#minecraft:enchantable/crossbow"
const val TRIDENT       = "#minecraft:enchantable/trident"
const val FISHING       = "#minecraft:enchantable/fishing"

// armor
/** All four armor pieces. */
const val ARMOR         = "#minecraft:enchantable/armor"
const val HEAD_ARMOR    = "#minecraft:enchantable/head_armor"
const val CHEST_ARMOR   = "#minecraft:enchantable/chest_armor"
const val LEG_ARMOR     = "#minecraft:enchantable/leg_armor"
const val FOOT_ARMOR    = "#minecraft:enchantable/foot_armor"

// tools
const val MINING        = "#minecraft:enchantable/mining"
const val MINING_LOOT   = "#minecraft:enchantable/mining_loot"

// universal
/** Anything with durability — the widest commonly used tag. */
const val DURABILITY    = "#minecraft:enchantable/durability"
const val VANISHING     = "#minecraft:enchantable/vanishing"
}