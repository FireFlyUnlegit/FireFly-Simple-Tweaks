package dev.firefly.simpletweaks.client

import dev.firefly.simpletweaks.enchantments.ModEnchantmentKeys
import dev.firefly.simpletweaks.network.packets.PacketLaser
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.util.hit.HitResult

/**
 * Client half of the `infinite_power` laser: turn a "swing at nothing" into a C2S [PacketLaser].
 *
 * Port of 1.12.2 `infinitepower/ClientHandler.kt` (24 lines), which was `@SideOnly(Side.CLIENT)` and
 * subscribed to Forge's `PlayerInteractEvent.LeftClickEmpty`. That event is listed as **not provided**
 * in `compat/event/PlayerInteractEvent.kt` ("a pure input event with no Fabric API equivalent"), so
 * this file reproduces it rather than depending on it.
 *
 * <h2>How `LeftClickEmpty` is reproduced, and why not the obvious way</h2>
 * Forge fired it when the player swung with **nothing under the crosshair**. The tempting Fabric
 * equivalent is `MinecraftClient.options.attackKey.wasPressed()` — but that is unusable here:
 * `MinecraftClient#handleInputEvents` drains the key with `while (attackKey.wasPressed()) doAttack()`
 * earlier in the same tick, so by the time `END_CLIENT_TICK` runs the counter is always back to zero.
 *
 * <p>So the edge is derived from `isPressed()` with a previous-state flag instead. `isPressed()` is
 * level-triggered and nothing else consumes it, which makes the edge exact rather than racy.
 *
 * <p>The crosshair test is the `LeftClickEmpty` condition: a `MISS` (or no hit) means the swing did
 * not land on a block or an entity. Note this is deliberately *not* "no entity in range" — a swing at
 * a block also fails it, which matches Forge.
 *
 * <h2>Why the client may send this at all</h2>
 * The client only decides *that* a laser was requested and in which direction. Whether it happens is
 * entirely the server's call: `fireLaser` re-checks the enchantment via
 * [dev.firefly.simpletweaks.enchantments.handlers.mythic.EnchantInfinitePowerHandler.fireLaser]'s
 * caller and re-traces the ray itself. A client that spams the packet cannot hit anything the server
 * would not have hit anyway.
 */
object InfinitePowerLaserClient {

    /** Previous tick's attack-key level, for edge detection. See the class KDoc. */
    private var wasAttackDown = false

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val down = client.options.attackKey.isPressed
            val justPressed = down && !wasAttackDown
            wasAttackDown = down
            if (!justPressed) return@register

            val player = client.player ?: return@register
            if (getItemSpecificEnchantLevel(player.mainHandStack, ModEnchantmentKeys.INFINITE_POWER) <= 0) {
                return@register
            }

            val hit = client.crosshairTarget
            if (hit != null && hit.type != HitResult.Type.MISS) return@register

            ClientPlayNetworking.send(PacketLaser(player.rotationVector))
        }
    }
}
