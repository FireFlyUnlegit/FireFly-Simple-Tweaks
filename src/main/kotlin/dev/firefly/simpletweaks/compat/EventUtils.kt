package dev.firefly.simpletweaks.compat

import dev.firefly.simpletweaks.compat.event.AttackEntityEvent
import dev.firefly.simpletweaks.compat.event.CriticalHitEvent
import dev.firefly.simpletweaks.compat.event.EventResult
import dev.firefly.simpletweaks.compat.event.LivingAttackEvent
import dev.firefly.simpletweaks.compat.event.LivingDamageEvent
import dev.firefly.simpletweaks.compat.event.LivingDeathEvent
import dev.firefly.simpletweaks.compat.event.LivingHealEvent
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.TickEvent
import net.minecraft.entity.LivingEntity
import kotlin.random.Random

/**
 * Port of the 1.12.2 `util/ForgeEventUtils.kt` extensions.
 *
 * The extension *names* are preserved on purpose — `e.invalid`, `e.attacker`, `e.target`,
 * `e.cancel()` and `chance(...)` appear across the handler package, so keeping them means the
 * handler bodies need only import-path edits during the bulk port.
 *
 * 1.21 Yarn mapping notes (all verified against the cached mappings):
 *  - `DamageSource.trueSource` -> `DamageSource.getAttacker()`  [method_5529]
 *  - `World.isRemote`          -> `World.isClient`              [field_9236, a FIELD not a method]
 *  - `EntityLivingBase`        -> `LivingEntity`
 *
 * Not yet ported (phase 1b, they need their own seams): `CriticalHitEvent`, `AttackEntityEvent`,
 * `TickEvent`, `PlayerEvent`, `LivingFallEvent`, `LivingDropsEvent`, `LivingEvent`.
 */

private fun LivingEntity.isClientSideWorld(): Boolean = WorldSide.isClient(this.world)

// ---------------------------------------------------------------- LivingAttackEvent

val LivingAttackEvent.isClientSide: Boolean get() = this.entityLiving.isClientSideWorld()
val LivingAttackEvent.attacker: LivingEntity? get() = this.source.attacker as? LivingEntity
val LivingAttackEvent.target: LivingEntity get() = this.entityLiving
val LivingAttackEvent.invalid: Boolean get() = this.isClientSide || this.isCanceled

// ---------------------------------------------------------------- LivingHurtEvent

val LivingHurtEvent.isClientSide: Boolean get() = this.entityLiving.isClientSideWorld()

/** Forge's `source.trueSource` — the entity ultimately responsible for the damage. */
val LivingHurtEvent.attacker: LivingEntity?
    get() = this.source.attacker as? LivingEntity

val LivingHurtEvent.target: LivingEntity
    get() = this.entityLiving

val LivingHurtEvent.invalid: Boolean
    get() = this.isClientSide || this.isCanceled

/** Forge's `setCanceled(true)` plus the amount zeroing the original helper did. */
fun LivingHurtEvent.cancel() {
    this.isCanceled = true
    this.amount = 0f
}

// ---------------------------------------------------------------- LivingDamageEvent

val LivingDamageEvent.isClientSide: Boolean get() = this.entityLiving.isClientSideWorld()
val LivingDamageEvent.attacker: LivingEntity? get() = this.source.attacker as? LivingEntity
val LivingDamageEvent.target: LivingEntity get() = this.entityLiving
val LivingDamageEvent.invalid: Boolean get() = this.isClientSide || this.isCanceled

fun LivingDamageEvent.cancel() {
    this.isCanceled = true
    this.amount = 0f
}

// ---------------------------------------------------------------- LivingDeathEvent

val LivingDeathEvent.isClientSide: Boolean get() = this.entityLiving.isClientSideWorld()
val LivingDeathEvent.attacker: LivingEntity? get() = this.source.attacker as? LivingEntity
val LivingDeathEvent.target: LivingEntity get() = this.entityLiving
val LivingDeathEvent.invalid: Boolean get() = this.isClientSide || this.isCanceled

// ---------------------------------------------------------------- LivingHealEvent

val LivingHealEvent.isClientSide: Boolean get() = this.entityLiving.isClientSideWorld()
val LivingHealEvent.target: LivingEntity get() = this.entityLiving
val LivingHealEvent.invalid: Boolean get() = this.isClientSide || this.isCanceled

fun LivingHealEvent.cancel() {
    this.isCanceled = true
    this.amount = 0f
}

// ---------------------------------------------------------------- CriticalHitEvent

/**
 * Port of the original helper. Note it does **not** merely flip a flag: forcing a crit also adds the
 * vanilla `0.5` multiplier, because Forge initialised `damageModifier` to `1.0` on a non-vanilla-crit
 * swing and to `1.5` on a vanilla one. Getting this wrong makes a forced crit do normal damage.
 */
fun CriticalHitEvent.setCrit(state: Boolean) {
    this.result = if (state) EventResult.ALLOW else EventResult.DENY
    if (state) this.damageModifier += 0.5f
}

val CriticalHitEvent.isCrit: Boolean
    get() = this.isVanillaCritical || this.result == EventResult.ALLOW

val CriticalHitEvent.isClientSide: Boolean get() = WorldSide.isClient(this.entityLiving.world)
val CriticalHitEvent.invalid: Boolean get() = this.isClientSide || this.isCanceled

// ---------------------------------------------------------------- AttackEntityEvent

/**
 * Port of the original helpers. Forge's `AttackEntityEvent.entity` was inherited from `PlayerEvent`
 * and held the attacking player, so it maps to [AttackEntityEvent.player] here.
 */
val AttackEntityEvent.isClientSide: Boolean get() = WorldSide.isClient(this.player.world)
val AttackEntityEvent.invalid: Boolean get() = this.isClientSide || this.isCanceled

// ---------------------------------------------------------------- TickEvent

/**
 * Faithful port of the original helper. Note the client-side clause: it means handlers that use
 * `e.invalid` run server-side only, which is what the 1.12.2 code relied on. Handlers that instead
 * filter only on `Phase.END` (e.g. `EnchantVoidProtectionHandler`) will also see the client-side
 * tick, so [dev.firefly.simpletweaks.compat.bridge.ClientEventBridge] must keep firing player ticks.
 */
val TickEvent.PlayerTickEvent.invalid: Boolean
    get() = this.phase != TickEvent.Phase.END || WorldSide.isClient(this.player.world)

val TickEvent.PlayerTickEvent.isClientSide: Boolean
    get() = WorldSide.isClient(this.player.world)

// ---------------------------------------------------------------- misc

fun chance(p: Float): Boolean = Random.nextFloat() < p
fun chance(p: Double): Boolean = Random.nextDouble() < p
