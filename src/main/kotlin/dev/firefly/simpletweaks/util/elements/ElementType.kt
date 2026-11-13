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

        private val byId: Map<String, ElementType> = entries.associateBy { it.id }

        fun byId(id: String): ElementType? = byId[id]

        /** The element for a stored ordinal, or `null` for [NONE_ORDINAL] / out of range. */
        fun ofOrdinal(ordinal: Int): ElementType? =
            if (ordinal < 0 || ordinal >= entries.size) null else entries[ordinal]

        /** The element of [source], or `null` when the damage is physical (no tag claims it). */
        fun of(source: DamageSource): ElementType? {
            val type = source.typeRegistryEntry
            for (element in entries) {
                if (type.isIn(element.tag)) return element
            }
            return null
        }
    }
}
