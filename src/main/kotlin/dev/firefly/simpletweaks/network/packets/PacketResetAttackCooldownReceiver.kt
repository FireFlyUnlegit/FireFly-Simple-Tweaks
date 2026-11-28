package dev.firefly.simpletweaks.network.packets

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.util.attackCharge
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking

/**
 * Client half of [PacketResetAttackCooldown] — spend *this* client's attack charge.
 *
 * `runPlayerAttack(..., simulateCooldown = true)` spends the charge on the server, but the HUD attack
 * indicator and vanilla's local hit prediction read the **client's** own `lastAttackedTicks`, which
 * vanilla only resets from the client's own attack path.
 *
 * The probe logs both ends of the transition so "the packet arrived" and "the charge actually moved"
 * are distinguishable (`chargeAfter` ≈ 0.04 for a 1.6 attack speed, not 0.0: a zero charge is
 * unreachable through this formula because vanilla's `+0.5F` base time is part of it).
 */
object PacketResetAttackCooldownReceiver {

    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(PacketResetAttackCooldown.ID) { _, context ->
            context.client().execute {
                val player = context.client().player ?: return@execute
                val chargeBefore = player.attackCharge
                player.resetLastAttackedTicks()
                FSTLog.log("KillAura") {
                    "outcome=client-charge-spent, chargeBefore=$chargeBefore, " +
                        "chargeAfter=${player.attackCharge}"
                }
            }
        }
    }
}
