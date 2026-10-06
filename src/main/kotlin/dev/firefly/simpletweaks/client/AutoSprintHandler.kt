package dev.firefly.simpletweaks.client

import dev.firefly.simpletweaks.compat.event.EventPriority
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.event.TickEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.core.config.GeneralConfig
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity

/**
 * Port of 1.12.2 `modules/AutoSprint.kt` (27 lines) — keeps the player sprinting while moving forward.
 *
 * <h2>1.12.2 original</h2>
 * <pre>
 *   object AutoSprint : Module("Sprint", "Movement") {
 *       private val omniSprint = boolean("OmniSprint", false)
 *       init {
 *           handler&lt;PlayerUpdateEvent&gt; {
 *               if (!isEnabled()) return@handler
 *               if (mc.player == null || mc.currentScreen != null) return@handler
 *               val player = mc.player
 *               if (player!!.movementInput.moveForward > 0.1f &amp;&amp; !player.isSneaking || omniSprint.get()) {
 *                   player.isSprinting = true
 *               }
 *           }
 *       }
 *       override fun onDisable() { mc.player?.isSprinting = false }
 *   }
 * </pre>
 *
 * <h2>No mixin is needed, and none of the four client mixins from phase-6 §3.1 are either</h2>
 * This is the whole of what is left of "batch 4" after the scope audit in
 * `docs/phase6-client-notes.md` §17. 1.12.2 fired `PlayerUpdateEvent` from
 * `MixinEntityPlayerSP#onLivingUpdate`; in this port [dev.firefly.simpletweaks.compat.bridge.ClientEventBridge]
 * already dispatches `TickEvent.PlayerTickEvent` for every client world player through
 * `ClientTickEvents.START/END_WORLD_TICK`, so the seam exists with **zero** new injection points.
 * The other 19 injection points in that audit turned out to serve events with no consumers, and
 * Velocity (the only other consumer) is not being ported.
 *
 * <h2>Mapping notes</h2>
 * | 1.12.2 | 1.21.1 |
 * |---|---|
 * | `EntityPlayerSP.movementInput.moveForward` | `ClientPlayerEntity.input.movementForward` |
 * | `EntityPlayerSP.isSprinting = true` | `ClientPlayerEntity.isSprinting = true` |
 * | `Minecraft.getMinecraft().player` | `MinecraftClient.getInstance().player` |
 * | `Module.isEnabled()` | `GeneralConfig.autoSprintEnabled` |
 * | `boolean("OmniSprint")` | `GeneralConfig.autoSprintOmniSprint` |
 *
 * <p>Only `Phase.END` is acted on, because 1.12.2's `PlayerUpdateEvent` fired **once** per client tick
 * (from `onLivingUpdate`) while this port fires both phases. Taking `END` is the equivalent single
 * point.
 *
 * <p>The local player is identified by identity against `MinecraftClient.player`, because
 * `ClientEventBridge` dispatches for every player in the client world, not just the local one.
 *
 * <p>`Module.onDisable()` set `isSprinting = false` immediately, so turning the option off does not
 * leave the player stuck sprinting until the next input change. That falling edge is reproduced here
 * rather than dropped, since a flattened config field has no `onDisable` hook of its own.
 */
object AutoSprintHandler : Listenable {

    /** Previous value of the option, so the disable transition can be detected. */
    private var wasEnabled = false

    @SubscribeEvent(priority = EventPriority.NORMAL)
    fun onPlayerTick(event: TickEvent.PlayerTickEvent) {
        if (event.phase != TickEvent.Phase.END) return

        val client = MinecraftClient.getInstance()
        val player = event.player as? ClientPlayerEntity ?: return
        // Identity check: the bridge dispatches for every player in the client world.
        if (player !== client.player) return

        if (!GeneralConfig.autoSprintEnabled) {
            if (wasEnabled) {
                player.isSprinting = false
            }
            wasEnabled = false
            return
        }
        wasEnabled = true

        // 1.12.2: `if (mc.player == null || mc.currentScreen != null) return`.
        if (client.currentScreen != null) return

        // Faithful to the original's precedence: && binds tighter than ||.
        if ((player.input.movementForward > 0.1f && !player.isSneaking)
            || GeneralConfig.autoSprintOmniSprint
        ) {
            player.isSprinting = true
        }
    }
}
