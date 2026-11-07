package dev.firefly.simpletweaks.compat

/**
 * One-shot channel from an `ArrowLooseEvent` handler to the `@ModifyArg` that scales vanilla's draw
 * progress.
 *
 * ## Why a holder and not a field on the event
 * Vanilla computes the shot's power **inside** `BowItem.onStoppedUsing`, *after* the event seam has
 * been posted, and it recomputes it from `remainingUseTicks` — the event's `charge` is a snapshot
 * that vanilla never reads back. The only way an enchantment can influence the shot is therefore to
 * hand a multiplier forward to the bytecode that computes it, which is what this is.
 *
 * Sequence for one release, all on the same thread:
 *  1. `BowItemArrowLooseMixin`'s `@Inject(HEAD)` resets [value] to `1.0`, then posts the event;
 *  2. a handler (e.g. fast_bow) sets it;
 *  3. `BowItemArrowLooseMixin`'s `@ModifyArg` on `getPullProgress` reads it and resets it again.
 *
 * ## Threading
 * A bow release is a single synchronous call on the side that runs it (the server for a real shot).
 * The field is intentionally not synchronised, and the reset in step 3 means a stale value cannot
 * leak into an unrelated release even if a future code path skipped the event.
 */
object ChargeBoost {

    /** Multiplier applied to the drawn-tick count before vanilla's `getPullProgress`. `1.0` = vanilla. */
    @JvmField
    var value: Float = 1.0f
}
