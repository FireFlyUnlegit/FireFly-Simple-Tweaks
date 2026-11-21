package dev.firefly.simpletweaks.util

import dev.firefly.simpletweaks.core.config.AttributeCapConfig
import dev.firefly.simpletweaks.mixin.accessors.ClampedEntityAttributeAccessor
import net.minecraft.entity.attribute.ClampedEntityAttribute
import net.minecraft.entity.attribute.EntityAttribute
import net.minecraft.registry.Registries

/**
 * Runtime side of [AttributeCapConfig]: maps an attribute instance to its configured bound and clamps
 * against it.
 *
 * Vanilla applies the cap inside [ClampedEntityAttribute.clamp], which `computeValue` calls at the end —
 * so a modifier cannot exceed the declared maximum no matter how large it is. Raising the bound therefore
 * has to happen at *this* level, which is what `ClampedEntityAttributeMixin` does.
 *
 * The default is **no upper bound**: an attribute with no configured entry tops out at
 * [Double.MAX_VALUE], which is what "break the 21 亿 ceiling" means in practice — the field still reads
 * whatever the pack's other mods set (AttributeFix and friends write 2147483647 there), so reading it
 * back as the default would silently keep their ceiling. The lower bound still defaults to vanilla's,
 * because a negative maximum health is a different kind of damage.
 */
object AttributeCaps {

    private val cache = HashMap<EntityAttribute, AttributeCapConfig.Cap?>()
    private var cachedRevision = -1

    /** `null` when this attribute has no override (the common case, and the fast path). */
    @JvmStatic
    fun capOf(attribute: EntityAttribute): AttributeCapConfig.Cap? {
        val revision = AttributeCapConfig.revision
        if (revision != cachedRevision) {
            cache.clear()
            cachedRevision = revision
        }
        if (cache.containsKey(attribute)) return cache[attribute]
        val id = Registries.ATTRIBUTE.getId(attribute)?.toString()
        val cap = if (id == null) null else AttributeCapConfig.of(id)
        cache[attribute] = cap
        return cap
    }

    @JvmStatic
    fun idOf(attribute: EntityAttribute): String? = Registries.ATTRIBUTE.getId(attribute)?.toString()

    /** The lower bound actually in force: the override when it has one, otherwise vanilla's. */
    @JvmStatic
    fun minOf(attribute: EntityAttribute): Double {
        capOf(attribute)?.min?.let { return it }
        return vanillaMin(attribute)
    }

    /** The upper bound actually in force; unbounded unless the config names one. */
    @JvmStatic
    fun maxOf(attribute: EntityAttribute): Double {
        capOf(attribute)?.max?.let { return it }
        return Double.MAX_VALUE
    }

    @JvmStatic
    fun vanillaMin(attribute: EntityAttribute): Double {
        val clamped = attribute as? ClampedEntityAttribute ?: return Double.NEGATIVE_INFINITY
        // Reading `minValue` through the getter would come straight back here: `getMinValue` is patched.
        return (clamped as ClampedEntityAttributeAccessor).simpletweaksReadMin()
    }

    /** The ceiling the game shipped with — *not* what is in force, since other mods may have raised it. */
    @JvmStatic
    fun vanillaMax(attribute: EntityAttribute): Double {
        val clamped = attribute as? ClampedEntityAttribute ?: return Double.POSITIVE_INFINITY
        return (clamped as ClampedEntityAttributeAccessor).simpletweaksReadMax()
    }

    /**
     * What vanilla would have done, with the override applied on top.
     *
     * Never calls [EntityAttribute.clamp] itself: this runs from that method's HEAD, so delegating would
     * re-enter it forever.
     */
    @JvmStatic
    fun clamp(attribute: EntityAttribute, value: Double): Double {
        val low = minOf(attribute)
        val high = maxOf(attribute)
        // A hand-edited file can invert the pair; `coerceIn` would throw, and the attribute would keep
        // the stale value forever because `computeValue` never finishes.
        return if (low <= high) value.coerceIn(low, high) else high
    }
}
