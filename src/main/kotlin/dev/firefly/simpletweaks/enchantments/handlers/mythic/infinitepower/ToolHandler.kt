package dev.firefly.simpletweaks.enchantments.handlers.mythic.infinitepower

import dev.firefly.simpletweaks.compat.event.PlayerEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.handlers.mythic.EnchantInfinitePowerHandler

/**
 * "Every tool" while holding `infinite_power`: everything is harvestable and everything breaks
 * instantly.
 *
 * Port of 1.12.2 `infinitepower/ToolHandler.kt` (28 lines). Both Forge events already have 1.21
 * replacements in this project, and both are documented as wired to real seams:
 *
 * | 1.12.2 | 1.21.1 | seam |
 * |---|---|---|
 * | `PlayerEvent.HarvestCheck` + `setCanHarvest(true)` | `PlayerEvent.HarvestCheck.canHarvest` | `@ModifyReturnValue` on `PlayerEntity#canHarvest` |
 * | `PlayerEvent.BreakSpeed` + `newSpeed` | `PlayerEvent.BreakSpeed.newSpeed` | `@ModifyReturnValue` on `getBlockBreakingSpeed` (both declaring classes) |
 *
 * <p>`Float.MAX_VALUE` is 1.12.2's value and is kept: the break-speed formula divides by the speed, so
 * any large finite number is already "instant", and matching the original avoids inventing a new
 * threshold.
 */
object ToolHandler : Listenable {

    @SubscribeEvent
    fun onHarvestCheck(e: PlayerEvent.HarvestCheck) {
        if (EnchantInfinitePowerHandler.heldLevel(e.player) > 0) {
            e.canHarvest = true
        }
    }

    @SubscribeEvent
    fun onBreakSpeed(e: PlayerEvent.BreakSpeed) {
        if (EnchantInfinitePowerHandler.heldLevel(e.player) > 0) {
            e.newSpeed = Float.MAX_VALUE
        }
    }
}
