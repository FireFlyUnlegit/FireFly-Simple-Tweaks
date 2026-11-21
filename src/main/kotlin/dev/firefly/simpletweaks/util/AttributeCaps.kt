package dev.firefly.simpletweaks.util

import dev.firefly.simpletweaks.core.config.AttributeCapConfig
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

    /** The upper bound actually in force. */
    @JvmStatic
    fun maxOf(attribute: EntityAttribute): Double {
        capOf(attribute)?.max?.let { return it }
        return vanillaMax(attribute)
    }

    @JvmStatic
    fun vanillaMin(attribute: EntityAttribute): Double =
        (attribute as? ClampedEntityAttribute)?.minValue ?: Double.NEGATIVE_INFINITY

    @JvmStatic
    fun vanillaMax(attribute: EntityAttribute): Double =
        (attribute as? ClampedEntityAttribute)?.maxValue ?: Double.POSITIVE_INFINITY

    /**
     * What vanilla would have done, with the override applied on top.
     *
     * Never calls [EntityAttribute.clamp] itself: this runs from that method's HEAD, so delegating would
     * re-enter it forever.
     */
    @JvmStatic
    fun clamp(attribute: EntityAttribute, value: Double): Double {
        val cap = capOf(attribute) ?: return value
        val low = cap.min ?: vanillaMin(attribute)
        val high = cap.max ?: vanillaMax(attribute)
        // A hand-edited file can invert the pair; `coerceIn` would throw, and the attribute would keep
        // the stale value forever because `computeValue` never finishes.
        return if (low <= high) value.coerceIn(low, high) else high
    }
}
