package dev.firefly.simpletweaks.enchantments.handlers.uncommon

import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.compat.event.EventPriority
import dev.firefly.simpletweaks.compat.event.PlayerEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.ModEnchantmentKeys
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.BlockPos
import java.util.WeakHashMap

/**
 * 1.21 port of `enchantments/handlers/uncommon/EnchantMomentumHandler.kt`.
 *
 * Line-by-line mapping from 1.12.2:
 * | 1.12.2                                                    | 1.21.1                                                    |
 * |-----------------------------------------------------------|-----------------------------------------------------------|
 * | `net.minecraftforge.event.entity.player.PlayerEvent.BreakSpeed` | `compat.event.PlayerEvent.BreakSpeed`               |
 * | `e.entityPlayer`                                          | `e.player`                                                |
 * | `e.heldItemMainhand`                                      | `p.mainHandStack` (`method_6047`)                          |
 * | `e.pos`                                                   | `e.pos` — now the **exact** block position (see below)      |
 * | `e.newSpeed`                                              | `e.newSpeed` (mutable property)                            |
 * | `EnchantMomentum` (Enchantment object)                    | `ModEnchantmentKeys.MOMENTUM` (RegistryKey)                |
 *
 * The 1.12.2 event carried `getPos()` (the block being mined) and this handler needs it to tell
 * "still mining the same block" from "started a new block" — the ramp only builds while the position
 * is unchanged. In 1.21 `PlayerEntity#getBlockBreakingSpeed` only receives the `BlockState`, so the
 * position is captured from `AbstractBlock#calcBlockBreakingDelta` (which does receive it) into a
 * thread-local and surfaced on the event. That is exact, not an approximation: matching on the block
 * state alone would conflate adjacent identical blocks (e.g. a field of stone).
 *
 * Behaviour preserved exactly: the first tick on a new position stores state and applies **no**
 * bonus; subsequent ticks increment and apply `newSpeed *= 1 + min(0.05 * lvl * ticks, lvl)`.
 */
object EnchantMomentumHandler : Listenable {

    private class DigState(val pos: BlockPos) {
        var ticks: Int = 1
    }

    private val digging = WeakHashMap<PlayerEntity, DigState>()

    @SubscribeEvent(priority = EventPriority.LOW)
    fun onBreakSpeed(e: PlayerEvent.BreakSpeed) {
        val p = e.player
        val lvl = getItemSpecificEnchantLevel(p.mainHandStack, ModEnchantmentKeys.MOMENTUM)
        if (lvl <= 0) {
            digging.remove(p)
            return
        }

        val pos = e.pos ?: return
        val state = digging[p]

        if (state == null || state.pos != pos) {
            digging[p] = DigState(pos)
            return
        }

        state.ticks++

        val bonus = (0.05f * lvl * state.ticks).coerceAtMost(1f * lvl)
        val speedBefore = e.newSpeed
        e.newSpeed *= (1f + bonus)
        STLog.log("Momentum") {
            "player=${p.name.string}, pos=$pos, lvl=$lvl, ticks=${state.ticks}, bonus=$bonus, " +
                "speed=$speedBefore->${e.newSpeed}"
        }
    }

    @SubscribeEvent
    fun onLogout(e: PlayerEvent.PlayerLoggedOutEvent) {
        digging.remove(e.player)
    }
}
