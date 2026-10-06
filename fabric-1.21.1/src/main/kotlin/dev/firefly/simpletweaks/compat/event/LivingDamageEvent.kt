package dev.firefly.simpletweaks.compat.event

import net.minecraft.entity.LivingEntity
import net.minecraft.entity.damage.DamageSource

/**
 * 1.21 replacement for Forge's `LivingDamageEvent` (16 usages).
 *
 * Forge fired this from `EntityLivingBase#applyDamage` — i.e. AFTER armor/absorption/enchantment
 * reduction, immediately before the health is actually subtracted. The seam is
 * [dev.firefly.simpletweaks.mixin.LivingEntityApplyDamageMixin] on `LivingEntity#applyDamage`.
 *
 * `amount` is mutable and is the value that will be subtracted from health; 6 handlers in the
 * 1.12.2 code rely on that (`EnchantDamageReductionHandler`, `EnchantDeathProtectionHandler`,
 * `EnchantEchoShieldHandler`, `EnchantGrievousWoundsHandler`, `EnchantResilienceHandler`,
 * `ForgeHandler`).
 *
 * Forge declared this event non-cancellable, but the old helper `ForgeEventUtils.cancel()` set
 * `isCanceled = true` and `amount = 0f` on it, so making it cancelable here is a superset of the
 * behaviour the handlers actually observed.
 */
class LivingDamageEvent(
    val entityLiving: LivingEntity,
    val source: DamageSource,
    var amount: Float
) : CancelableEvent()
