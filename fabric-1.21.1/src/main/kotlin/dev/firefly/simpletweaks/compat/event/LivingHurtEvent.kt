package dev.firefly.simpletweaks.compat.event

import net.minecraft.entity.LivingEntity
import net.minecraft.entity.damage.DamageSource

/**
 * 1.21 replacement for Forge's `LivingHurtEvent`.
 *
 * Fired from [dev.firefly.simpletweaks.mixin.EntityDamageMixin] at the head of
 * `Entity#damage(DamageSource, float)` — the same position Forge used (pre-armor, pre-invulnerability,
 * before the target's own damage logic).
 *
 * `amount` is the damage about to be applied and is mutable, mirroring Forge.
 */
class LivingHurtEvent(
    /** The entity being hurt. 1.12.2 name kept so handler bodies port unchanged. */
    val entityLiving: LivingEntity,
    val source: DamageSource,
    var amount: Float
) : CancelableEvent()
