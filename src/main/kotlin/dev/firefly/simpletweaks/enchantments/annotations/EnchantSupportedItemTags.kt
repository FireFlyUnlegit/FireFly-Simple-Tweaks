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

/**
 * Everything the eight element conversions can go on: swords, bows and crossbows.
 *
 * A **mod-owned** tag (`data/simple_tweaks/tags/item/conversion_weapons.json`) rather than a vanilla one,
 * because no vanilla tag contains exactly "melee weapon + both ranged weapons", and the conversion has to
 * stay one enchantment id shared by all of them (`build=cleanup25`).
 */
const val CONVERSION_WEAPONS = "#simple_tweaks:conversion_weapons"

// ranged
const val BOW           = "#minecraft:enchantable/bow"

/**
 * Bows **and** crossbows — the tags the arrow-based enchantments declare.
 *
 * A mod-owned tag for the same reason as [CONVERSION_WEAPONS]: no vanilla tag means "bow or crossbow".
 * Every arrow enchantment (`piercing_arrow`, `echo_shot`, `tracking_arrow`, `sniper`, `hunters_mark`,
 * `multishot`, `starfall`) works purely off the arrow that lands, so restricting them to bows was a
 * mechanical accident, not a design choice. `fast_bow` deliberately stays on [BOW]: it shortens the **draw**
 * time, and a crossbow has a reload instead of a draw. Its equivalent for the crossbow would need a
 * separate charge-time hook.
 */
const val BOW_OR_CROSSBOW = "#simple_tweaks:bow_enchantable"
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

/**
 * Elytra **only**.
 *
 * `#minecraft:enchantable/durability` is "anything with durability", which includes the mace, shields and every
 * tool — using it for a glider enchantment is what let `gale_elytra` appear on a mace (`build=cleanup53`). No
 * vanilla tag means "elytra", so this mod-owned one exists for the single enchantment that needs it.
 */
const val ELYTRA        = "#simple_tweaks:elytra"
const val VANISHING     = "#minecraft:enchantable/vanishing"
}