package dev.firefly.simpletweaks.compat.event

import net.minecraft.entity.LivingEntity
import net.minecraft.entity.damage.DamageSource

/**
 * 1.21 replacement for Forge's `LivingAttackEvent` (1 usage in the 1.12.2 code).
 *
 * Forge fired this immediately before `LivingHurtEvent`, and cancelling it prevented the damage
 * outright. Both are now fired from the same HEAD injector in
 * [dev.firefly.simpletweaks.mixin.EntityDamageMixin], in the same order.
 */
class LivingAttackEvent(
    val entityLiving: LivingEntity,
    val source: DamageSource,
    val amount: Float
) : CancelableEvent()
