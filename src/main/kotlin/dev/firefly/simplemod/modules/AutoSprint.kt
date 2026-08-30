package dev.firefly.simplemod.modules

import dev.firefly.simplemod.core.Module
import dev.firefly.simplemod.core.event.PlayerUpdateEvent
import dev.firefly.simplemod.core.handler
import net.minecraft.client.Minecraft

object AutoSprint : Module("Sprint", "Movement") {
    private val omniSprint = boolean("OmniSprint",false)
    private val mc = Minecraft.getMinecraft()

    init {
        handler<PlayerUpdateEvent> {
            if (!isEnabled()) return@handler
            if (mc.player == null || mc.currentScreen != null) return@handler

            val player = mc.player
            if (player!!.movementInput.moveForward > 0.1f && !player.isSneaking || omniSprint.get()) {
                player.isSprinting = true
            }
        }
    }

    override fun onDisable() {
        mc.player?.isSprinting = false
    }
}