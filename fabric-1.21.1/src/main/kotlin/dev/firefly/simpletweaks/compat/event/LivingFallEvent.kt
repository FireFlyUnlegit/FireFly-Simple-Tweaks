package dev.firefly.simpletweaks.compat.event

import net.minecraft.entity.LivingEntity

/**
 * 1.21 replacement for Forge's `LivingFallEvent` (1 usage: `EnchantVoidProtectionHandler`, which
 * cancels fall damage).
 *
 * <p><b>Seam target:</b> the mappings show
 * `handleFallDamage(float, float, DamageSource): boolean` declared on `net/minecraft/entity/Entity`
 * (`method_5747`) — again <b>not</b> on `LivingEntity`. Cancelling maps to returning `false`, which
 * is exactly how vanilla suppresses fall damage.
 *
 * <p><b>[distance] and [damageMultiplier] are read-only, deliberately.</b> Forge let handlers mutate
 * both, but wiring that up needs two `@ModifyVariable` injectors on two consecutive `float`
 * parameters, whose extra-argument capture would be ambiguous (only the ordinal distinguishes them).
 * The single 1.12.2 consumer only cancels, so mutation is not wired and the fields are `val` rather
 * than `var` — a `var` that silently does nothing would be worse than a read-only one. If a future
 * handler needs to scale a fall, add `@ModifyVariable(argsOnly = true, ordinal = 0)` for the
 * distance (and `ordinal = 1` for the multiplier).
 */
class LivingFallEvent(
    val entityLiving: LivingEntity,
    val distance: Float,
    val damageMultiplier: Float
) : CancelableEvent()
