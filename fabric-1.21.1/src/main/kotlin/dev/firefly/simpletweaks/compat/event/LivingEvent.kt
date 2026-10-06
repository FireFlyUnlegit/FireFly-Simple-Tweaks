package dev.firefly.simpletweaks.compat.event

import net.minecraft.entity.LivingEntity

/**
 * 1.21 replacement for Forge's `LivingEvent` hierarchy (3 usages: `EnchantCombatMasterHandler`,
 * `EnchantHeavenlyPunishmentHandler`, `EnchantEchoShieldHandler`).
 *
 * `LivingUpdateEvent` is bridged from a mixin at the head of `Entity#tick()`.
 *
 * <p><b>Why `Entity` and not `LivingEntity`:</b> the cached yarn mappings show `tick()` declared
 * only on `net/minecraft/entity/Entity` (`method_5773`, official `l`) — `LivingEntity` does
 * <b>not</b> override it. So a `@Mixin(Entity.class)` HEAD inject on `tick()V` is the method that
 * actually executes for a living entity; injecting into `LivingEntity#tick` would not even resolve.
 * A `LivingEntity` instanceof filter then excludes non-living entities.
 */
sealed class LivingEvent(val entityLiving: LivingEntity) : Event() {

    class LivingUpdateEvent(entityLiving: LivingEntity) : LivingEvent(entityLiving)
}
