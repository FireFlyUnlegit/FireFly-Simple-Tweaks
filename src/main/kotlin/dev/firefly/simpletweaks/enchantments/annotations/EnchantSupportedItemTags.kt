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

/**
 * Axes **only**.
 *
 * ⚠️ **There is no `#minecraft:enchantable/axe`.** 1.21.1 ships exactly 19 `enchantable` tags and axes
 * are reachable only through `#minecraft:enchantable/sharp_weapon` (shared with swords) or
 * `#minecraft:enchantable/mining` (shared with pickaxes/shovels/hoes/shears). Declaring the nonexistent
 * name is silent: the tag resolves to an **empty list**, `isPrimaryItem` answers `false` for every
 * stack, and the enchantment can never be applied to anything — that is how
 * `shield_breaker` shipped inert (`build=cleanup68`).
 *
 * So this is a **mod-owned** tag (`data/simple_tweaks/tags/item/axe_enchantable.json`) whose only entry
 * is `#minecraft:axes`. Using `sharp_weapon` directly would also put axe-only enchantments on swords,
 * which is a different design.
 */
const val AXE           = "#simple_tweaks:axe_enchantable"
const val FIRE_ASPECT   = "#minecraft:enchantable/fire_aspect"

/**
 * Everything the seven element conversions can go on: swords, bows and crossbows.
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

/**
 * Hoes **only** (`#minecraft:hoes`), and pickaxes **only**, via mod-owned tags.
 *
 * 1.21.1 has no `enchantable/hoe` / `enchantable/pickaxe` (same gap as [AXE]): the vanilla item tags are
 * `#minecraft:hoes` / `#minecraft:pickaxes`, and the only *enchantable* tags that contain them are the
 * wide `mining` (everything) and `sharp_weapon` (swords + axes). Restricting `bountiful` / `gleaner` to
 * one tool each is the design, so they get a two-line mod tag instead of a wide vanilla one.
 */
const val HOE           = "#simple_tweaks:hoe_enchantable"
const val PICKAXE       = "#simple_tweaks:pickaxe_enchantable"

/**
 * Shields **only**.
 *
 * There is **no** vanilla `#minecraft:shields` item tag (verified against the 1.21.1 jar: the file does
 * not exist), and `enchantable/durability` is every durable item. Same reasoning as [ELYTRA].
 */
const val SHIELD        = "#simple_tweaks:shield_enchantable"

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