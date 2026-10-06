package dev.firefly.simpletweaks.compat.bridge

import dev.firefly.simpletweaks.compat.ForgeEventBus
import dev.firefly.simpletweaks.compat.event.AttackEntityEvent
import dev.firefly.simpletweaks.compat.event.BlockEvent
import dev.firefly.simpletweaks.compat.event.EntityJoinWorldEvent
import dev.firefly.simpletweaks.compat.event.PlayerEvent
import dev.firefly.simpletweaks.compat.event.PlayerInteractEvent
import dev.firefly.simpletweaks.compat.event.TickEvent
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.util.ActionResult
import net.minecraft.util.TypedActionResult

/**
 * Translates Fabric API callbacks into the Forge-shaped events in `compat.event`, so the 1.12.2
 * handlers keep working unchanged.
 *
 * Signature source of truth: the Fabric API **sources** jars for 0.116.17+1.21.1
 * (`fabric-lifecycle-events-v1-2.6.0`, `fabric-entity-events-v1-1.8.0`), not documentation:
 * <pre>
 *   ServerTickEvents.START_SERVER_TICK  : onStartTick(MinecraftServer)
 *   ServerTickEvents.END_SERVER_TICK    : onEndTick(MinecraftServer)
 *   ServerTickEvents.START_WORLD_TICK   : onStartTick(ServerWorld)
 *   ServerTickEvents.END_WORLD_TICK     : onEndTick(ServerWorld)
 *   ServerPlayerEvents.JOIN             : onJoin(ServerPlayerEntity)
 *   ServerPlayerEvents.LEAVE            : onLeave(ServerPlayerEntity)
 *   ServerPlayerEvents.COPY_FROM        : copyFromPlayer(old, new, alive)
 *   ServerPlayerEvents.AFTER_RESPAWN    : afterRespawn(old, new, alive)
 *   ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD : afterChangeWorld(player, origin, destination)
 * </pre>
 *
 * <p>Note the class is `ServerEntityWorldChange**s**` / `...WorldChangeEvents`. It is NOT
 * `ServerEntityLevelChangeEvents` — guessing that name would have failed to compile.
 *
 * <p>Player ticks are derived from the world tick by iterating that world's players. Forge fired
 * `PlayerTickEvent` per player from `EntityPlayerMP#onUpdate`; iterating the world's player list on
 * each world tick gives the same per-player-per-tick coverage without needing a mixin, and works
 * identically on the client via [ClientEventBridge].
 */
object ServerEventBridge {

    fun register() {
        ServerTickEvents.START_SERVER_TICK.register { _ ->
            ForgeEventBus.post(TickEvent.ServerTickEvent(TickEvent.Phase.START))
        }
        ServerTickEvents.END_SERVER_TICK.register { _ ->
            ForgeEventBus.post(TickEvent.ServerTickEvent(TickEvent.Phase.END))
        }

        ServerTickEvents.START_WORLD_TICK.register { world ->
            ForgeEventBus.post(TickEvent.WorldTickEvent(TickEvent.Phase.START, world))
            for (player in world.players) {
                ForgeEventBus.post(TickEvent.PlayerTickEvent(TickEvent.Phase.START, player))
            }
        }
        ServerTickEvents.END_WORLD_TICK.register { world ->
            for (player in world.players) {
                ForgeEventBus.post(TickEvent.PlayerTickEvent(TickEvent.Phase.END, player))
            }
            ForgeEventBus.post(TickEvent.WorldTickEvent(TickEvent.Phase.END, world))
        }

        ServerPlayerEvents.JOIN.register { player ->
            ForgeEventBus.post(PlayerEvent.PlayerLoggedInEvent(player))
        }
        ServerPlayerEvents.LEAVE.register { player ->
            ForgeEventBus.post(PlayerEvent.PlayerLoggedOutEvent(player))
        }
        ServerPlayerEvents.COPY_FROM.register { oldPlayer, newPlayer, alive ->
            // Forge direction: `player` = new, `original` = old.
            //
            // ⚠️ `alive` is NEGATED on purpose. Fabric's javadoc (sources jar for
            // fabric-entity-events-v1 1.8.0) says: "@param alive whether the OLD player is still
            // alive" — so a death respawn passes alive = false, while Forge's `isWasDeath()` is true
            // exactly then. Passing `alive` straight through inverted the flag, and every handler that
            // guards on it (`EnchantSoulBoundHandler`, `EnchantEchoShieldHandler`,
            // `EnchantExtraArmorHandler`, `EnchantRegenerationHandler`, `EnchantDelayedRecoveryHandler`)
            // silently took the "not a death" branch.
            //
            // It was found the hard way: SoulBound withheld the item from the death drops and then
            // never restored it, so the item simply vanished. Confirmed on a real client — the log had
            // `[ST-SoulBound] ... outcome=withheld-from-drops` and no `outcome=restored-on-respawn`.
            ForgeEventBus.post(PlayerEvent.Clone(newPlayer, oldPlayer, !alive))
        }
        ServerPlayerEvents.AFTER_RESPAWN.register { _, newPlayer, _ ->
            ForgeEventBus.post(PlayerEvent.PlayerRespawnEvent(newPlayer, false))
        }
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register { player, _, _ ->
            ForgeEventBus.post(PlayerEvent.PlayerChangedDimensionEvent(player))
        }

        // --- interaction / world ---------------------------------------------------------------
        // Forge's AttackEntityEvent was cancellable; FAIL suppresses the attack, PASS lets vanilla run.
        AttackEntityCallback.EVENT.register { player, _, _, entity, _ ->
            val event = AttackEntityEvent(player, entity)
            ForgeEventBus.post(event)
            if (event.isCanceled) ActionResult.FAIL else ActionResult.PASS
        }

        // BEFORE returning false cancels the break, matching Forge's BlockEvent.BreakEvent cancellation.
        PlayerBlockBreakEvents.BEFORE.register { world, player, pos, state, _ ->
            val event = BlockEvent.BreakEvent(world, pos, state, player)
            ForgeEventBus.post(event)
            !event.isCanceled
        }

        // Server-side only; see EntityJoinWorldEvent's note about the missing client-side hook.
        ServerEntityEvents.ENTITY_LOAD.register { entity, world ->
            ForgeEventBus.post(EntityJoinWorldEvent(entity, world))
        }

        UseItemCallback.EVENT.register { player, world, hand ->
            val stack = player.getStackInHand(hand)
            val event = PlayerInteractEvent.RightClickItem(player, world, hand, stack)
            ForgeEventBus.post(event)
            if (event.isCanceled) TypedActionResult.fail(stack) else TypedActionResult.pass(stack)
        }
    }
}
