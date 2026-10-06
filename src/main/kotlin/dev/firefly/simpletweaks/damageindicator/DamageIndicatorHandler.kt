package dev.firefly.simpletweaks.damageindicator

import dev.firefly.simpletweaks.compat.event.EventPriority
import dev.firefly.simpletweaks.compat.event.LivingDamageEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.core.config.DamageIndicatorConfig
import dev.firefly.simpletweaks.core.registerEvents
import dev.firefly.simpletweaks.network.NetworkManager
import dev.firefly.simpletweaks.network.packets.PacketDamageIndicator
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.entity.LivingEntity
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import java.util.UUID
import kotlin.math.abs

/**
 * Server half of the damage indicator: decides *what* numbers to show and sends them.
 *
 * <h2>Design (1.12.2's, preserved)</h2>
 * This is **not** an event-driven "report the damage" system. It is a **health-differ**:
 * <ol>
 *   <li>on [LivingDamageEvent] it remembers the damage value and a timestamp per entity UUID;</li>
 *   <li>at the end of every server tick it walks each world's players, gathers the [LivingEntity]s
 *       within `DamageIndicatorConfig.maxDistance` and compares each entity's
 *       `health + absorptionAmount` against the value it saw last tick;</li>
 *   <li>a difference bigger than `0.001` becomes a number — positive is a heal, negative is damage —
 *       and is broadcast to every player within 64 blocks.</li>
 * </ol>
 * The step-1 value is only used as the **"original"** number (the pre-clamp figure) when it arrived
 * within 200 ms, which is how "overkill" can be rendered as `original(actual)`.
 *
 * <h2>Why the step-1 seam is [LivingDamageEvent] and not [dev.firefly.simpletweaks.compat.event.LivingHurtEvent]</h2>
 * 1.12.2 hooked Forge's `LivingHurtEvent`. In Forge 1.12.2 that fires **after**
 * `applyArmorCalculations` and `applyPotionDamageCalculations` and **before** absorption is
 * subtracted. The port's two candidate seams do not sit in the same place:
 * <pre>
 *   compat.event.LivingHurtEvent   -> Entity#damage HEAD          = BEFORE armor   (not equivalent)
 *   compat.event.LivingDamageEvent -> LivingEntity#applyDamage    = after armor+resistance, before absorption
 * </pre>
 * So [LivingDamageEvent] is the positionally correct analogue, and the number this handler stores
 * matches what 1.12.2 stored. Using `LivingHurtEvent` here would have silently inflated every
 * "original" figure by the target's armor factor — the same class of mistake recorded in
 * `docs/phase4-mixin-notes.md`.
 *
 * <h2>Deliberate differences from 1.12.2</h2>
 * <ul>
 *   <li>{@code DimensionManager.getWorlds()} → {@code MinecraftServer#getWorlds()} (1.12.2 iterated
 *       every loaded dimension; 1.21 exposes them on the server).</li>
 *   <li>`getEntitiesWithinAABB` → {@code World#getNonSpectatingEntities}. 1.12.2 had no spectator
 *       mode, so this is the closest available equivalent; the effect is that spectators neither
 *       receive numbers for themselves nor act as scan centres.</li>
 *   <li>The 1.12.2 `FMLCommonHandler.instance().minecraftServerInstance == null` guard is gone —
 *       {@code END_SERVER_TICK} only fires when there is a server.</li>
 * </ul>
 */
object DamageIndicatorHandler : Listenable {

    private val lastRealHealth = mutableMapOf<UUID, Float>()
    private val lastOriginalDamage = mutableMapOf<UUID, Float>()
    private val lastDamageTime = mutableMapOf<UUID, Long>()

    override fun init() {
        ServerTickEvents.END_SERVER_TICK.register { server -> onServerTickEnd(server) }
    }

    /**
     * 1.12.2 `onLivingHurt`, retargeted at [LivingDamageEvent] — see the class KDoc.
     *
     * `LOWEST` priority is kept because 1.12.2 used it: other handlers get to modify the damage
     * first, so the recorded "original" is the value after every other enchantment's adjustment.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onLivingDamage(event: LivingDamageEvent) {
        if (!DamageIndicatorConfig.enabled) return
        val target = event.entityLiving
        // 1.12.2 guarded on `event.isClientSide`; kept so the client half of an integrated server
        // does not populate the maps.
        if (target.world.isClient) return

        val uuid = target.uuid
        lastOriginalDamage[uuid] = event.amount
        lastDamageTime[uuid] = System.currentTimeMillis()
    }

    /** 1.12.2 `onServerTick` (`Phase.END` only). */
    private fun onServerTickEnd(server: MinecraftServer) {
        if (!DamageIndicatorConfig.enabled) return

        val maxDist = DamageIndicatorConfig.maxDistance.toDouble()
        val now = System.currentTimeMillis()

        val aliveUUIDs = HashSet<UUID>()
        val processedThisTick = HashSet<UUID>()

        for (world in server.worlds) {
            val playersInWorld = world.players
            if (playersInWorld.isEmpty()) continue

            for (player in playersInWorld) {
                val box = player.boundingBox.expand(maxDist, maxDist, maxDist)
                val entities = world.getNonSpectatingEntities(LivingEntity::class.java, box)

                for (entity in entities) {
                    val uuid = entity.uuid
                    aliveUUIDs.add(uuid)

                    // An entity near two players must only be processed once per tick, otherwise the
                    // second player would see a zero diff and nothing (or a stale diff) would fire.
                    if (!processedThisTick.add(uuid)) continue

                    val currentRealHealth = entity.health + entity.absorptionAmount
                    val currentRealMaxHealth = entity.maxHealth + entity.absorptionAmount
                    val lastRealHealthValue = lastRealHealth[uuid]

                    if (lastRealHealthValue != null) {
                        val diff = currentRealHealth - lastRealHealthValue

                        if (abs(diff) > 0.001f) {
                            val isHeal = diff > 0
                            val amount = abs(diff)

                            val originalDamage = lastOriginalDamage[uuid]
                            val damageTime = lastDamageTime[uuid]
                            val recentDamage = damageTime != null && now - damageTime < 200

                            val finalOriginalDamage =
                                if (recentDamage && originalDamage != null) originalDamage else amount

                            val actualDamage =
                                if (isHeal) amount else amount.coerceAtMost(lastRealHealthValue)
                            val isOverkill = !isHeal && finalOriginalDamage > lastRealHealthValue

                            val afterRealHealth = if (isHeal) {
                                (lastRealHealthValue + actualDamage).coerceAtMost(currentRealMaxHealth)
                            } else {
                                (lastRealHealthValue - actualDamage).coerceAtLeast(0f)
                            }

                            sendToNearbyPlayers(
                                entity,
                                actualDamage,
                                finalOriginalDamage,
                                isHeal,
                                isOverkill,
                                entity.maxHealth,
                                currentRealMaxHealth,
                                afterRealHealth,
                            )

                            if (recentDamage) {
                                lastOriginalDamage.remove(uuid)
                                lastDamageTime.remove(uuid)
                            }
                        }
                    }

                    lastRealHealth[uuid] = currentRealHealth
                }
            }
        }

        // Drop entries for entities that are no longer alive/nearby, otherwise the maps grow without
        // bound over a long session.
        lastRealHealth.keys.removeAll { it !in aliveUUIDs }
        lastOriginalDamage.keys.removeAll { it !in aliveUUIDs }
        lastDamageTime.keys.removeAll { it !in aliveUUIDs }
    }

    /** 1.12.2 `sendToNearbyPlayers` — everyone within 64 blocks, including the target itself. */
    private fun sendToNearbyPlayers(
        target: LivingEntity,
        actualDamage: Float,
        originalDamage: Float,
        isHeal: Boolean,
        isOverkill: Boolean,
        maxHealth: Float,
        realMaxHealth: Float,
        afterRealHealth: Float,
    ) {
        val players = target.world.getNonSpectatingEntities(
            ServerPlayerEntity::class.java,
            target.boundingBox.expand(64.0),
        )
        for (player in players) {
            val packet = PacketDamageIndicator(
                target.id,
                actualDamage,
                originalDamage,
                isHeal,
                isOverkill,
                player === target,
                maxHealth,
                realMaxHealth,
                afterRealHealth,
            )
            NetworkManager.sendToClient(packet, player)
        }
    }
}
