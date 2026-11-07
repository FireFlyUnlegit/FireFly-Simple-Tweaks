package dev.firefly.simpletweaks.util.elements

import dev.firefly.simpletweaks.SimpleTweaks
import dev.firefly.simpletweaks.util.DamageTags
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.damage.DamageType
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.tag.TagKey
import net.minecraft.util.Identifier

/**
 * The mod's eight elements, and the single source of truth for "which element is this damage?".
 *
 * ## Why an enum and a tag per member
 * The enums carry the *identity* (id, colour, conversion formula); the [TagKey] carries the *data*
 * (which vanilla and mod damage types count as this element). Keeping them in separate layers is what
 * lets a datapack add `minecraft:on_fire` to `#simple_tweaks:elemental_fire` without a code change,
 * which matters because vanilla keeps adding damage types.
 *
 * ## Physical is the default, not a whitelist
 * [PHYSICAL] is special: **any damage type that belongs to none of the other seven elements is
 * physical.** That is the author's rule ("物理伤害就是最基础的伤害"): a sword hit, a cactus, a
 * falling anvil and a fireball from a mod we have never heard of are all physical unless we say
 * otherwise. A whitelist would silently classify every new damage type as "no element", and the
 * physical protection enchantment would stop covering it with nobody noticing.
 *
 * ⚠️ Vanilla's own elemental types are still listed explicitly, in the *other* seven tags, because
 * "physical by default" and "is fire" are different questions: `minecraft:in_fire` is physical by the
 * default rule only if nothing claims it, so the fire tag must claim it.
 */
enum class ElementType(
    /** Registry path; the enchantment id is `<id>_conversion` / `<id>_protection`. */
    val id: String,
    /** Display key fragment: `enchantment.simple_tweaks.element.<id>`. */
    val tag: TagKey<DamageType>,
    /**
     * `0xRRGGBB` for this element's **floating damage number**.
     *
     * Kept on the enum rather than in the renderer so the packet only has to carry an ordinal — a
     * colour on the wire would be a second source of truth to keep in sync with the particle burst.
     * [PHYSICAL] deliberately keeps the indicator's pre-existing red, so an ordinary sword hit looks
     * exactly as it did before this feature existed.
     */
    val color: Int,
) {
    /**
     * The baseline element. Never converted *into* by an enchantment that needs a source, but
     * `physical_conversion` exists and converts other elements **back to** physical.
     */
    PHYSICAL("physical", DamageTags.ELEMENTAL_PHYSICAL, 0xFF3333),
    FIRE("fire", DamageTags.ELEMENTAL_FIRE, 0xFF6A00),
    FROST("frost", DamageTags.ELEMENTAL_FROST, 0x7FE3FF),
    LIGHTNING("lightning", DamageTags.ELEMENTAL_LIGHTNING, 0xFFE84D),
    WATER("water", DamageTags.ELEMENTAL_WATER, 0x4DA6FF),
    NATURE("nature", DamageTags.ELEMENTAL_NATURE, 0x66DD55),
    DARK("dark", DamageTags.ELEMENTAL_DARK, 0xA96BFF),
    LIGHT("light", DamageTags.ELEMENTAL_LIGHT, 0xFFF2A8);

    /** `simple_tweaks:element_<id>` — the damage type a conversion re-deals part of the hit as. */
    val damageType: RegistryKey<DamageType>
        get() = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(SimpleTweaks.MOD_ID, "element_$id"))

    /** The other seven, i.e. everything a `xx_conversion` can convert *from*. */
    fun others(): List<ElementType> = entries.filter { it != this }

    companion object {

        /** Every element that has an `xx_conversion` enchantment, i.e. all eight. */
        val CONVERTIBLE: List<ElementType> = entries.toList()

        /**
         * Elements a **mob** can be assigned as its attack element — everything except [PHYSICAL].
         *
         * Physical is excluded because "converting a physical hit into physical" is a no-op: a mob with
         * the physical element would be indistinguishable from a mob with no element at all, which is
         * exactly the ambiguity this list removes.
         */
        val ATTACKABLE: List<ElementType> = entries.filter { it != PHYSICAL }

        /** Sentinel for "no element", used in NBT and in the damage-indicator packet. */
        const val NONE_ORDINAL = -1

        private val byId: Map<String, ElementType> = entries.associateBy { it.id }

        fun byId(id: String): ElementType? = byId[id]

        /**
         * The element for a stored ordinal, or `null` for [NONE_ORDINAL] / an out-of-range value.
         *
         * Out-of-range returns `null` instead of throwing: the ordinal crosses both an NBT boundary and
         * a network boundary, and a stale save (or a client one version behind) must degrade to "no
         * element" rather than crash a mob's tick or the renderer.
         */
        fun ofOrdinal(ordinal: Int): ElementType? =
            if (ordinal < 0 || ordinal >= entries.size) null else entries[ordinal]

        /**
         * The element of [source], or [PHYSICAL] when no tag claims it.
         *
         * Order matters only for overlapping tags (e.g. `wither` is in both `magic_damage` and
         * `elemental_dark`; that overlap is deliberate and does not involve this function). Within
         * this enum, [entries] order decides, and no two element tags are intended to overlap — if
         * they ever do, the earlier one wins and a `warn` is not emitted (there is nowhere sensible to
         * put one; a datapack author can overlap tags freely).
         *
         * Reads tag membership, so it needs a world/registry — it is never called on the client side
         * of a dedicated server, and in single player both sides have the registry.
         */
        fun of(source: DamageSource): ElementType {
            val type = source.typeRegistryEntry
            for (element in entries) {
                if (element == PHYSICAL) continue
                if (type.isIn(element.tag)) return element
            }
            return PHYSICAL
        }
    }
}
