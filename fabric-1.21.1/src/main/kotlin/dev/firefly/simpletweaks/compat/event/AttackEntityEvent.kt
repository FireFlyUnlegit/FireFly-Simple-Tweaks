package dev.firefly.simpletweaks.compat.event

import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity

/**
 * 1.21 replacement for Forge's `AttackEntityEvent` (4 usages).
 *
 * Forge declared this as a subclass of `PlayerEvent`; here it extends [CancelableEvent] directly and
 * exposes `player` itself. Nothing in the 1.12.2 code relies on `AttackEntityEvent is PlayerEvent`,
 * and extending `Event` directly keeps the sealed [PlayerEvent] hierarchy closed.
 *
 * Forge's version was cancellable and cancelling it suppressed the attack; the bridge maps that onto
 * `AttackEntityCallback` returning `ActionResult.FAIL`.
 */
class AttackEntityEvent(
    val player: PlayerEntity,
    val target: Entity
) : CancelableEvent()
