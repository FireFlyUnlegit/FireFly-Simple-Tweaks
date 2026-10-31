package dev.firefly.simpletweaks.util

import net.minecraft.entity.LivingEntity
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.util.Identifier

/**
 * Shared plumbing for handing out **absorption** in 1.21.1 — the one place that knows about the
 * `generic.max_absorption` clamp.
 *
 * ## Why a helper exists at all
 * 1.12.2 let a mod write the absorption field directly, so ports of that code read like this:
 *
 * ```kotlin
 * entity.absorptionAmount += amount        // 1.12.2: works
 * ```
 *
 * In 1.21.1 that write goes through `LivingEntity#setAbsorptionAmount(float)`, which is
 * `public final` and whose **first act** is `MathHelper.clamp(value, 0.0f, getMaxAbsorption())`.
 * `getMaxAbsorption()` reads `EntityAttributes.GENERIC_MAX_ABSORPTION`, registered as
 * `ClampedEntityAttribute("generic.max_absorption", 0.0, 0.0, 2048.0)` — **default 0**. Nothing raises
 * it except the vanilla `absorption` status effect (`addAttributeModifier(…, 4.0, ADD_VALUE)`), which
 * is why `absorption` is normally bounded by the golden apple that gave it.
 *
 * So with no absorption effect active, every direct write is silently clamped to `0`: the grant never
 * lands, the code compiles, and there is no exception and no log. This is `AGENTS.md` §4.9's blood
 * debt (`golden_shield` never worked for one frame) generalised into a primitive.
 *
 * ## The two rules §4.9 imposes, both encoded here
 * 1. **Raise the ceiling before writing.** [ensureAbsorptionHeadroom] attaches a modifier of our own,
 *    so the clamp admits the value. It is **temporary**, never persistent: its removal deadline lives
 *    in the caller's in-memory state, and a persistent modifier whose remover can be lost is exactly
 *    the bug that once left `heavenly_punishment` victims permanently unable to attack (§4.2).
 * 2. **Account for the landed delta, not the requested one.** [grantAbsorption] returns
 *    `after - before`, re-read from the entity. A caller that books the delta it *asked* for will
 *    claim success while granting nothing — which is how `golden_shield` also convinced itself it was
 *    at cap and stopped trying.
 *
 * ## Modifier ids are per source, and that matters
 * The headroom modifiers are additive, so several sources stack instead of overwriting each other.
 * A single shared id would make the last writer win and silently clamp the other source away, so
 * every caller passes its own id (see [absorptionHeadroomId]).
 *
 * ## One caveat to keep in mind when choosing the headroom value
 * The clamp applies to the **total** absorption, and a foreign share (a golden apple) is *not* covered
 * by our modifier. A caller that passes only its own cap therefore loses part of the grant whenever
 * the player already holds foreign absorption — which is why `healing_blade` passes
 * `foreign + ownCap`. Whatever is passed:
 *
 * - over-estimating is harmless (the ceiling only *permits* absorption; it never adds any), and
 * - when the modifier is removed again, a foreign share survives only if its own source raises
 *   `max_absorption` too — true for the vanilla `absorption` effect, which carries its own modifier.
 */
fun absorptionHeadroomId(source: String): Identifier =
    Identifier.of("simple_tweaks", "${source}_max_absorption")

/**
 * Raises `generic.max_absorption` to at least [value] for [entity], so that a write to
 * [LivingEntity.getAbsorptionAmount] is not clamped away.
 *
 * Monotonic within a session: an existing modifier that already covers [value] is left alone, so a
 * changing cap does not churn the attribute every call. Use [removeAbsorptionHeadroom] to retire it.
 */
fun ensureAbsorptionHeadroom(entity: LivingEntity, modifierId: Identifier, value: Float) {
    val attribute = entity.getAttributeInstance(EntityAttributes.GENERIC_MAX_ABSORPTION) ?: return
    val existing = attribute.getModifier(modifierId)
    if (existing != null && existing.value >= value.toDouble()) return

    attribute.removeModifier(modifierId)
    attribute.addTemporaryModifier(
        EntityAttributeModifier(modifierId, value.toDouble(), EntityAttributeModifier.Operation.ADD_VALUE)
    )
}

/**
 * Retires the headroom modifier added by [ensureAbsorptionHeadroom]. Returns whether one was present.
 *
 * Removing it lowers `max_absorption` again, which makes `LivingEntity#onAttributeChanged` clamp the
 * current absorption down — so callers must subtract **their own** share *before* calling this, or the
 * clamp will eat the foreign share instead. A no-op when nothing is attached, so it is cheap enough to
 * call unconditionally.
 */
fun removeAbsorptionHeadroom(entity: LivingEntity, modifierId: Identifier): Boolean {
    val attribute = entity.getAttributeInstance(EntityAttributes.GENERIC_MAX_ABSORPTION) ?: return false
    if (attribute.getModifier(modifierId) == null) return false
    attribute.removeModifier(modifierId)
    return true
}

/**
 * Grants [requested] absorption to [entity] **and reports what actually landed**.
 *
 * [headroom] is the value handed to [ensureAbsorptionHeadroom]; see the file KDoc for how to choose it
 * (the caller's own cap, plus any foreign share it wants to keep room for).
 *
 * The return value is `after - before`, re-read after the write. Anything less than [requested] means
 * something else clamped the write, and the caller is expected to record that as its own outcome
 * rather than silently booking the request.
 */
fun grantAbsorption(entity: LivingEntity, modifierId: Identifier, requested: Float, headroom: Float): Float {
    if (requested <= 0f) return 0f
    ensureAbsorptionHeadroom(entity, modifierId, headroom)

    val before = entity.absorptionAmount
    entity.absorptionAmount = before + requested
    return entity.absorptionAmount - before
}
