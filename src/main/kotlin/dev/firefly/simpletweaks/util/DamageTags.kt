package dev.firefly.simpletweaks.util

import net.minecraft.entity.damage.DamageType
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.tag.TagKey
import net.minecraft.util.Identifier

/**
 * Damage-type tags this mod owns.
 *
 * ## `magic_damage`
 * The tag `magic_protection` keys on: vanilla's own magic-ish types.
 *
 * ## `elemental_*` — the eight element tags
 * Each element has one tag. Membership is how an incoming hit is classified as that element, and it is
 * what both the `xx_protection` enchantments and the reaction system read.
 *
 * ### Two kinds of membership, and why it matters
 * A tag points at damage **types**, and there are two sources of them:
 *
 * | source | example | who can put it in the tag |
 * |---|---|---|
 * | vanilla type | `minecraft:in_fire`, `minecraft:freeze`, `minecraft:lightning_bolt` | this mod, via a tag file |
 * | this mod's own type | `simple_tweaks:element_fire`, … | this mod, via a tag file |
 *
 * The `xx_conversion` enchantments re-deal part of a hit as `simple_tweaks:element_*`, so that type
 * **must** be in its own element tag or the conversion would not count as its own element. The vanilla
 * types are included as well so that a real fireball counts as `FIRE` for protection and reactions
 * without the player needing the conversion enchantment at all.
 *
 * ⚠️ The tag files live at `data/simple_tweaks/tags/damage_type/<id>.json`, **not**
 * `data/simple_tweaks/damage_type/<id>.json` — the latter is a damage-type *definition* and will fail
 * the whole registry load (`DEV_GUIDE` §6 #29).
 *
 * ## Why these constants used to be dead
 * Four of them (`ELEMENTAL_FIRE` / `FROST` / `LIGHTNING` / `EXPLOSION`) were declared long before
 * anything read them — no tag files shipped and no handler referenced them. `docs/acceptance-checklist.md`
 * recorded that as a known blank. They are live as of the elemental batch: `ELEMENTAL_EXPLOSION` was
 * **deleted** (explosive damage is physical for our purposes — see [ElementType.physical]), and the
 * other three are now shipped and read.
 */
object DamageTags {

    /** Vanilla magic-ish types. Read by `magic_protection`. */
    val MAGIC_DAMAGE: TagKey<DamageType> =
        TagKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of("simple_tweaks", "magic_damage"))

    /**
     * Fire: vanilla burning / lava / fireballs, plus this mod's conversion type.
     *
     * Deliberately **not** `#minecraft:is_fire`: that tag is vanilla's own and includes
     * `minecraft:campfire` and friends, which are fine, but this mod also needs its own
     * `element_fire` type in the same set, and extending a vanilla tag from a mod file is exactly the
     * "silently ignored" trap `DEV_GUIDE` §6 #15 records (`values` without `replace` **appends**, which
     * is what we want — but the file must be ours, not vanilla's).
     */
    val ELEMENTAL_FIRE: TagKey<DamageType> =
        TagKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of("simple_tweaks", "elemental_fire"))

    /** Ice / cold: `freeze` (powder snow), plus this mod's conversion type. */
    val ELEMENTAL_FROST: TagKey<DamageType> =
        TagKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of("simple_tweaks", "elemental_frost"))

    /** Lightning: vanilla lightning bolt, plus this mod's conversion type. */
    val ELEMENTAL_LIGHTNING: TagKey<DamageType> =
        TagKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of("simple_tweaks", "elemental_lightning"))

    /**
     * Water: drowning, plus this mod's conversion type.
     *
     * Note this is "the water element", not "damage while wet". A player standing in water hit by a
     * sword takes `player_attack`, which is [ElementType.physical] — that is the whole point of having
     * a physical element that is the default.
     */
    val ELEMENTAL_WATER: TagKey<DamageType> =
        TagKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of("simple_tweaks", "elemental_water"))

    /** Nature: cactus / sweet berry bush / stalagmite, plus this mod's conversion type. */
    val ELEMENTAL_NATURE: TagKey<DamageType> =
        TagKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of("simple_tweaks", "elemental_nature"))

    /**
     * Dark: wither, sonic boom, plus this mod's conversion type.
     *
     * `magic_damage` and `elemental_dark` overlap on purpose (`wither`, `sonic_boom`): `magic_protection`
     * is an existing enchantment with its own meaning ("reduce magic"), and dark protection is a new,
     * narrower one. Overlapping tags mean an item with both reduces both — which is correct, not a bug.
     */
    val ELEMENTAL_DARK: TagKey<DamageType> =
        TagKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of("simple_tweaks", "elemental_dark"))

    /** Light: this mod's conversion type only — vanilla has no "holy" damage type. */
    val ELEMENTAL_LIGHT: TagKey<DamageType> =
        TagKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of("simple_tweaks", "elemental_light"))

    /**
     * Physical: **the absence of every other element**, plus explicit vanilla melee types.
     *
     * This is the most important and the least obvious tag. Two roles:
     *
     * 1. **`physical_protection` reads it.** It cannot be "any damage", because then it would stack
     *    with every elemental protection; it means "blunt/melee/environmental", i.e. the damage that is
     *    *not* one of the other seven elements.
     * 2. **Zero-length and default-safe.** Every type not listed in any of the eight files is physical
     *    by default — see [ElementType]. This file only lists the types we want to be explicit about;
     *    it is **not** the definition of physical.
     */
    val ELEMENTAL_PHYSICAL: TagKey<DamageType> =
        TagKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of("simple_tweaks", "elemental_physical"))
}
