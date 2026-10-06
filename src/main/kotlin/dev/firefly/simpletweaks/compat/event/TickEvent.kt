package dev.firefly.simpletweaks.compat.event

import net.minecraft.entity.player.PlayerEntity
import net.minecraft.world.World

/**
 * 1.21 replacement for Forge's `net.minecraftforge.fml.common.gameevent.TickEvent` (26 usages —
 * the largest single group in the 1.12.2 code).
 *
 * The nested subclasses keep Forge's exact type names so handler bodies port unchanged
 * (`TickEvent.PlayerTickEvent`, `TickEvent.Phase.END`, `e.phase`, `e.player`, `e.world`).
 *
 * Unlike the damage events this hierarchy is NOT flat: each tick event extends [TickEvent].
 * That is safe for dispatch because every handler subscribes to a concrete subclass
 * (18x `PlayerTickEvent`, 6x `WorldTickEvent`, 2x `ServerTickEvent`) and none subscribes to the
 * base type.
 *
 * Wiring is done entirely through Fabric API callbacks in
 * [dev.firefly.simpletweaks.compat.bridge.ServerEventBridge] /
 * [dev.firefly.simpletweaks.compat.bridge.ClientEventBridge] — no mixin is needed for ticking.
 *
 * [Phase.START] still exists for source compatibility, but note that **no 1.12.2 handler uses it**
 * (every one filters on `Phase.END`), so the START events are fired for fidelity only.
 */
sealed class TickEvent(val phase: Phase) : Event() {

    enum class Phase {
        START,
        END
    }

    class ServerTickEvent(phase: Phase) : TickEvent(phase)

    class ClientTickEvent(phase: Phase) : TickEvent(phase)

    class WorldTickEvent(phase: Phase, val world: World) : TickEvent(phase)

    class PlayerTickEvent(phase: Phase, val player: PlayerEntity) : TickEvent(phase)

    /** Only used by the damage-indicator renderer; [renderPartialTicks] mirrors Forge's member. */
    class RenderTickEvent(phase: Phase, val renderPartialTicks: Float) : TickEvent(phase)
}
