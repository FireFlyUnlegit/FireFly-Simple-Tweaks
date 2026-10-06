package dev.firefly.simpletweaks.compat.event

import net.minecraft.entity.LivingEntity
import net.minecraft.entity.damage.DamageSource

/**
 * 1.21 replacement for Forge's `LivingDeathEvent` (4 usages).
 *
 * Forge fired this at the head of `EntityLivingBase#onDeath`, cancellable — cancelling kept the
 * entity alive (it reset health). Seam: [dev.firefly.simpletweaks.mixin.LivingEntityDeathMixin]
 * on `LivingEntity#onDeath(DamageSource)`.
 */
class LivingDeathEvent(
    val entityLiving: LivingEntity,
    val source: DamageSource
) : CancelableEvent()
