package dev.firefly.simpletweaks.compat.event

import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity

/**
 * 1.21 replacement for Forge's `CriticalHitEvent` (3 usages: [EnchantCritHandler],
 * [EnchantDoubleCritHandler], [EnchantCritDamageHandler], plus the crit block of
 * `util/PlayerUtils.runPlayerAttack`).
 *
 * <h2>Where Forge fired it</h2>
 * `EntityPlayer#attackTargetEntityWithCurrentItem`, at the exact point where vanilla decides whether
 * the swing is a critical hit:
 * ```java
 * boolean flag2 = flag && this.fallDistance > 0.0F && !this.onGround && !this.isOnLadder() && ...;
 * flag2 = flag2 && !this.isSprinting();
 * CriticalHitEvent hitResult = ForgeHooks.getCriticalHit(this, target, flag2, flag2 ? 1.5F : 1.0F);
 * flag2 = hitResult != null;
 * if (flag2) { damage *= hitResult.getDamageModifier(); }
 * ```
 * and `ForgeHooks.getCriticalHit` returned the event only when
 * `result == ALLOW || (vanillaCritical && result == DEFAULT)`. A `null` return meant "not a crit".
 *
 * <h2>Seam</h2>
 * [dev.firefly.simpletweaks.mixin.PlayerEntityAttackMixin] on `PlayerEntity#attack(Entity)`:
 * the event is fired from `@ModifyArg` on the `Entity#damage` call (i.e. after every early return,
 * exactly like Forge), and vanilla's own `f *= 1.5F` is neutralised by `@ModifyConstant` so the
 * Forge multiplier is applied exactly once. See [dev.firefly.simpletweaks.compat.EventSeams].
 *
 * <h2>Deviations from Forge</h2>
 *  - Forge's `CriticalHitEvent` extends `PlayerEvent` extends `LivingEvent`, so the 1.12.2 handlers
 *    reach the player through `e.entityLiving`. That accessor is kept (as a synonym of
 *    [entityPlayer]) so the handler bodies port unchanged.
 *  - Forge's `Event.Result` tri-state is re-created here as [EventResult]; there is no Forge base
 *    class to inherit it from.
 *  - `isCanceled` exists on the base class but Forge's `CriticalHitEvent` is not `@Cancelable`, so it
 *    stays false — the `invalid` guard the handlers use is therefore driven by the client-side check
 *    only, exactly as in 1.12.2.
 */
class CriticalHitEvent(
    /** Forge's `PlayerEvent#getPlayer()`. */
    val entityPlayer: PlayerEntity,
    /** Forge's `getTarget()`. */
    val target: Entity,
    /** Forge's `isVanillaCritical()` — whether vanilla itself decided this swing crits. */
    val isVanillaCritical: Boolean,
    /**
     * The attack charge (`getAttackCooldownProgress(0.5F)`) **as it was when the swing started**.
     *
     * This is NOT `entityPlayer.attackCharge`: that property reads the value live, and by the time
     * this event fires `PlayerEntity#attack` has already reset the cooldown timer, so a live read
     * returns ≈0.5 instead of ≈1.0 and any `charge >= 0.848` gate silently fails.
     * `EnchantDoubleCritHandler` was losing every roll to exactly that before this field existed.
     *
     * 1.12.2 had the same requirement and met it with a mixin: `MixinEntityPlayer` `@Redirect`-ed the
     * `getCooledAttackStrength(0.5F)` call inside `attackTargetEntityWithCurrentItem` into
     * `AttackChargeAccessor`, so `safeGetCooledAttackStrength()` returned the captured value. Here the
     * capture happens at the HEAD of `PlayerEntity#attack` — see
     * [dev.firefly.simpletweaks.mixin.PlayerEntityAttackMixin].
     */
    val attackCharge: Float,
) : Event() {

    /**
     * Forge initialised this to the crit multiplier it passed in: `1.5` when vanilla would crit,
     * else `1.0`. Handlers add to it, so a forced crit lands on exactly `1.5`.
     */
    var damageModifier: Float = if (isVanillaCritical) 1.5f else 1.0f

    /** The value [damageModifier] was constructed with (Forge's `getOriginalDamage()`). */
    val originalDamage: Float = damageModifier

    /**
     * Forge set `Result.ALLOW` in the constructor when `vanillaCritical`, which is why
     * `getCriticalHit` returned the event for a plain vanilla crit.
     */
    var result: EventResult = if (isVanillaCritical) EventResult.ALLOW else EventResult.DEFAULT

    /** Forge's `LivingEvent#getEntityLiving()` — the attacking player. */
    val entityLiving: PlayerEntity get() = entityPlayer
}

/** Forge's `net.minecraftforge.fml.common.eventhandler.Event.Result`. */
enum class EventResult {
    DEFAULT,
    ALLOW,
    DENY
}
