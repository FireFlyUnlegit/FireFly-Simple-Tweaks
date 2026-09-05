package dev.firefly.simplemod.util

import net.minecraftforge.event.entity.player.CriticalHitEvent
import net.minecraftforge.fml.common.eventhandler.Event

fun CriticalHitEvent.setCrit(state: Boolean) {
    this.result = if (state) Event.Result.ALLOW else Event.Result.DENY
    this.damageModifier += 0.5f
}
val CriticalHitEvent.isCrit: Boolean
    get() = this.isVanillaCritical || this.result == Event.Result.ALLOW