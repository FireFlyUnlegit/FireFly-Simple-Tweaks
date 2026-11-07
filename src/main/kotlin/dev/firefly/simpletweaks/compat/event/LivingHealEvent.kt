package dev.firefly.simpletweaks.compat.event

import net.minecraft.entity.LivingEntity

/**
 * 1.21 replacement for Forge's `LivingHealEvent` (3 usages).
 *
 * `amount` is mutable: `EnchantDelayedRecoveryHandler` does `heal.amount -= tickAmount`, which is
 * how that enchantment staggers its regeneration. Seam:
 * [dev.firefly.simpletweaks.mixin.LivingEntityHealMixin] on `LivingEntity#heal(float)`.
 */
class LivingHealEvent(
    val entityLiving: LivingEntity,
    var amount: Float
) : CancelableEvent()
