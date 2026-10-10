package dev.firefly.simpletweaks.compat

import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.network.packet.s2c.play.EntityStatusEffectS2CPacket
import net.minecraft.server.network.ServerPlayerEntity

/**
 * Re-sends an effect whose **duration was edited in place** to its owner's client.
 *
 * <h2>Why this is needed at all</h2>
 * `StatusEffectInstance#duration` is a plain field, and the only thing that ever pushes a changed
 * duration to the client is the `addStatusEffect` path: it ends in
 * `LivingEntity#onStatusEffectApplied` / `#onStatusEffectUpgraded`, which `ServerPlayerEntity`
 * overrides to send an `EntityStatusEffectS2CPacket` to **its own** connection (verified from
 * bytecode -- the base class only notifies *passengers* via `sendEffectToControllingPlayer`, so the
 * self-send really is the player's override).
 *
 * `StatusEffectInstanceAccessor` exists to write that field directly, which is exactly what
 * `blessing_extension` and `curse_resistance` do. Bypassing `addStatusEffect` therefore also bypasses
 * the packet: the client keeps ticking down the duration it was last told about and the two silently
 * diverge. Beneficial effects then end early *on the client* (the HUD icon drops, and client-side
 * rendering such as night vision switches off) while the server still has them; harmful ones linger
 * on the client after the server dropped them.
 *
 * ## `addStatusEffect` is not an alternative
 * It runs the new instance through `StatusEffectInstance#upgrade`, which only accepts a **longer**
 * duration at equal amplifier. That happens to work for `blessing_extension` (+1 tick) but can never
 * express `curse_resistance` (-1 tick), and it would also allocate a fresh instance per proc, losing
 * the original's hidden-effect chain. Sending the packet keeps the in-place mutation and works in
 * both directions.
 *
 * ## Target set and flags
 * **Owner only**, matching vanilla: other players get the effect's particle colours through the
 * `POTION_SWIRLS` tracked data (the DataTracker), which the duration does not affect.
 * `keepFading = false` mirrors `ServerPlayerEntity#onStatusEffectUpgraded` -- the effect is already
 * visible, so the client must not replay its fade-in.
 *
 * ## Rate
 * One packet per changed effect per proc, so the cost is bounded by the proc chance in the callers
 * (`0.05 x level` per tick, i.e. at most ~4 procs/s at level 4) rather than by the tick rate.
 */
fun ServerPlayerEntity.syncEffectToClient(effect: StatusEffectInstance) {
    networkHandler.sendPacket(EntityStatusEffectS2CPacket(id, effect, false))
}
