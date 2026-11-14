package dev.firefly.simpletweaks.util.elements

import dev.firefly.simpletweaks.SimpleTweaks
import dev.firefly.simpletweaks.util.DamageTags
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.damage.DamageType
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.registry.tag.TagKey
import net.minecraft.util.Identifier
import java.util.Optional

/**
 * The mod's seven elements. Physical damage is **not an element**: it is what is left when no tag claims a
 * damage type, so [of] returns `null` for it. It can therefore never carry an aura or take part in a
 * reaction.
 */
enum class ElementType(
    val id: String,
    val tag: TagKey<DamageType>,
    /** `0xRRGGBB` for the floating damage number. */
    val color: Int,
) {
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

    fun others(): List<ElementType> = entries.filter { it != this }

    companion object {

        /** Colour of a physical (non-elemental) floating number: the indicator's pre-existing red. */
        const val PHYSICAL_COLOR = 0xFF3333

        /** Elements a mob can be assigned as its attack element. */
        val ATTACKABLE: List<ElementType> = entries.toList()

        /** Every element that has an `xx_conversion` enchantment. */
        val CONVERTIBLE: List<ElementType> = entries.toList()

        /** Sentinel for "no element", used in NBT and in the damage-indicator packet. */
        const val NONE_ORDINAL = -1

        /**
         * Memoised [classify]; `Optional.empty()` is the stored form of `null`, so a cached miss is a hit too.
         *
         * ⚠️ The miss marker must be distinguishable from every real element. Both earlier attempts
         * encoded it as an element (`entries[0]` = FIRE, then an anonymous subclass): the first made
         * every cached **hit** on FIRE read back as a miss, the second cannot exist at all because an
         * `enum` is final — both caught by `elementClassificationMemoMatchesAFreshScan` (`build=cleanup12`).
         * `ConcurrentHashMap` forbids null values, hence `Optional`.
         */
        private val BY_TYPE: MutableMap<RegistryKey<DamageType>, Optional<ElementType>> =
            java.util.concurrent.ConcurrentHashMap()

        private val byId: Map<String, ElementType> = entries.associateBy { it.id }

        fun byId(id: String): ElementType? = byId[id]

        /** The element for a stored ordinal, or `null` for [NONE_ORDINAL] / out of range. */
        fun ofOrdinal(ordinal: Int): ElementType? =
            if (ordinal < 0 || ordinal >= entries.size) null else entries[ordinal]

        /** The element of [source], or `null` when the damage is physical (no tag claims it). */
        fun of(source: DamageSource): ElementType? = classify(source.typeRegistryEntry)

        /**
         * Entry point taking the inner [DamageType] value.
         *
         * `DamageSource#getTypeRegistryEntry` hands back a **new** `RegistryEntry.Reference` wrapper each
         * call, so two anonymous registry entries with the same key are usually the same value but a
         * fresh wrapper. Memoising on `equals` therefore compares by value, not by wrapper identity.
         */
        @JvmStatic
        fun classify(type: DamageType): ElementType? = classify(RegistryEntry.of(type))

        /**
         * [of] against a bare [RegistryEntry], memoised by the entry's **key**.
         *
         * A single melee hit asks this 15..40 times (`onLivingHurt`, the mitigation row, the indicator,
         * every reaction listener), and each miss costs up to seven tag lookups. The set of damage types
         * in a world is small and fixed, so one lookup per type is enough. Tag contents *can* change
         * under a datapack reload — [clearCache] is the invalidation, wired to server start and to the
         * client's join/disconnect.
         *
         * ⚠️ Keyed on `RegistryKey` and **not** on the `DamageType` value: `DamageType` is a record, and
         * `minecraft:campfire` / `minecraft:in_fire` have identical fields (`msgId=inFire`), so a
         * value-keyed memo answers one with the other's tag result — caught by
         * `elementClassificationMemoMatchesAFreshScan` (`build=cleanup12`).
         */
        @JvmStatic
        fun classify(type: RegistryEntry<DamageType>): ElementType? {
            val key = type.key.orElse(null)
            // Not every entry is registered (ad-hoc sources are not), so those always scan.
            if (key == null) return scan(type)
            BY_TYPE[key]?.let { return it.orElse(null) }
            val found = scan(type)
            BY_TYPE[key] = Optional.ofNullable(found)
            return found
        }

        private fun scan(type: RegistryEntry<DamageType>): ElementType? {
            for (element in entries) {
                if (type.isIn(element.tag)) return element
            }
            return null
        }

        /** Drops the memoised classification; call when damage-type tags may have changed. */
        @JvmStatic
        fun clearCache() {
            BY_TYPE.clear()
        }
    }
}
