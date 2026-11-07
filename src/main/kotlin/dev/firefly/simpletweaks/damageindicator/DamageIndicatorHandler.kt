package dev.firefly.simpletweaks.damageindicator

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.event.LivingDamageEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.core.config.DamageIndicatorConfig
import dev.firefly.simpletweaks.core.registerEvents
import dev.firefly.simpletweaks.network.NetworkManager
import dev.firefly.simpletweaks.network.packets.PacketDamageIndicator
import dev.firefly.simpletweaks.util.elements.ElementCombat
import dev.firefly.simpletweaks.util.elements.ElementType
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
 *   <li>at the end of every server tick it walks each world's players, gathers the [LivingEntity]s
 *       within `DamageIndicatorConfig.maxDistance` and compares each entity's
 *       `health + absorptionAmount` against the value it saw last tick;</li>
 *   <li>a difference bigger than `0.001` becomes a number — positive is a heal, negative is damage —
 *       and is broadcast to every player within 64 blocks. A delta **below
 *       `DamageIndicatorConfig.minHealthChange`** (non-zero only when the user raised it) is skipped
 *       entirely: no packet (`build=cleanup26`).</li>
 * </ol>
 * The measured delta **is** the whole number. Nothing recorded on a damage event reaches the client any
 * more (`build=cleanup47`): the health difference is taken after every handler, absorption and clamp has
 * run, so the value on screen is exactly what the health bar did. See `PacketDamageIndicator` for why the
 * 1.12.2 "original damage" figure was deleted instead of fixed — it was a pre-clamp number and could be
 * three times the health the target actually had left.
 *
 * <h2>Why [LivingDamageEvent] is still subscribed</h2>
 * Only for the optional physical/elemental split (`DamageIndicatorConfig.splitElemental`): the health
 * differ cannot tell a converted hit's two halves apart, because it only ever sees the total. The event
 * is used as a *composition* record (which element contributed how much this tick), never as the
 * displayed value.
 *
 * <h2>Why that seam is [LivingDamageEvent] and not [dev.firefly.simpletweaks.compat.event.LivingHurtEvent]</h2>
 * 1.12.2 hooked Forge's `LivingHurtEvent`. In Forge 1.12.2 that fires **after**
 * `applyArmorCalculations` and `applyPotionDamageCalculations` and **before** absorption is
 * subtracted. The port's two candidate seams do not sit in the same place:
 * <pre>
 *   compat.event.LivingHurtEvent   -> Entity#damage HEAD          = BEFORE armor   (not equivalent)
 *   compat.event.LivingDamageEvent -> LivingEntity#applyDamage    = after armor+resistance, before absorption
 * </pre>
 * So [LivingDamageEvent] is the positionally correct analogue, and it is the seam that makes the split
 * components comparable with each other (both are post-armour). Using `LivingHurtEvent` here would have
 * silently inflated every component by the target's armor factor — the same class of mistake recorded in
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

    /**
     * Entities currently below `minHealthChange`, so the suppression probe logs on **entry** only.
     * Purely a logging concern — the filtering itself is stateless.
     */
    private val suppressedEntities = HashSet<UUID>()

    override fun init() {
        ServerTickEvents.END_SERVER_TICK.register { server -> onServerTickEnd(server) }
    }

    /**
     * 1.12.2 `onLivingHurt`, retargeted at [LivingDamageEvent] — see the class KDoc.
     *
     * `priority = 100` (`LOWEST`) is kept because 1.12.2 used it: other handlers get to modify the damage
     * first, so the composition recorded here is the value after every other enchantment's adjustment.
     */
    @SubscribeEvent(priority = 100)
    fun onLivingDamage(event: LivingDamageEvent) {
        if (!DamageIndicatorConfig.enabled) return
        val target = event.entityLiving
        // 1.12.2 guarded on `event.isClientSide`; kept so the client half of an integrated server
        // does not populate the maps.
        if (target.world.isClient) return

        val uuid = target.uuid

        // Composition for the split display. `LivingDamageEvent` fires once per `applyDamage`, i.e. once
        // per *damage instance*, so a converted hit shows up here as one physical instance and one
        // elemental instance and the two are summed independently. That is the whole reason the split is
        // possible at all: the health differ below only ever sees the total.
        if (DamageIndicatorConfig.splitElemental) {
            val bucket = composition.getOrPut(uuid) { Composition() }
            val element = ElementType.of(event.source)
            if (element == ElementType.PHYSICAL) {
                bucket.physical += event.amount
            } else {
                // One bucket **per element**, not one shared "elemental" sum. With a single sum a hit
                // carrying water + lightning (conversion rider + `heavenly_punishment`) collapsed into
                // one number coloured by whichever element landed last — the reported
                // "打开不合并之后还是只有两个数字".
                bucket.elemental.merge(element.ordinal, event.amount, Float::plus)
            }
        }
    }

    /** One entity's per-tick damage broken down by element. */
    private class Composition {
        var physical: Float = 0f

        /** `element ordinal -> damage this tick`, in arrival order so the numbers stay stable. */
        val elemental: LinkedHashMap<Int, Float> = LinkedHashMap()

        /** Total of every elemental share this tick; the over-clamp rescale needs the sum. */
        fun elementalTotal(): Float {
            var total = 0f
            for (value in elemental.values) total += value
            return total
        }
    }

    /**
     * Per-entity, per-tick composition buckets.
     *
     * Entries are removed when consumed and the whole map is cleared each tick when the option is off,
     * so it cannot grow: its size is bounded by "entities damaged this tick".
     */
    private val composition = HashMap<UUID, Composition>()

    /** 1.12.2 `onServerTick` (`Phase.END` only). */
    private fun onServerTickEnd(server: MinecraftServer) {
        if (!DamageIndicatorConfig.enabled) return

        val maxDist = DamageIndicatorConfig.maxDistance.toDouble()
        // Read once per tick; 0 means "no filtering" (the pre-cleanup26 behaviour).
        val minChange = DamageIndicatorConfig.minHealthChange.toFloat()

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

                            if (minChange > 0f && amount < minChange) {
                                // Below the configured floor (`DamageIndicatorConfig.minHealthChange`):
                                // no number at all.
                                // Probe on the *transition* only: a suppressed delta repeats every tick
                                // while poison/regen ticks away, and the log must not flood (AGENTS §4.6).
                                if (suppressedEntities.add(uuid)) {
                                    FSTLog.log("DamageIndicator") {
                                        "outcome=below-threshold, diff=${"%.3f".format(diff)}, " +
                                            "amount=${"%.3f".format(amount)}, min=$minChange"
                                    }
                                }
                            } else {
                                suppressedEntities.remove(uuid)

                                // `cleanup47`: the number is the health that actually moved, and nothing
                                // else. There used to be an "original" figure recorded on the damage seam
                                // and rendered as `original(actual)` when it exceeded the remaining health;
                                // it was a *pre-clamp* value, so a 150-damage proc on a 48 HP mob read as
                                // "150" while the bar fell by 48. See `PacketDamageIndicator` for the full
                                // argument. `actualDamage` is capped at the pre-hit health because that is
                                // the most a health bar can lose.
                                val actualDamage =
                                    if (isHeal) amount else amount.coerceAtMost(lastRealHealthValue)

                                val afterRealHealth = if (isHeal) {
                                    (lastRealHealthValue + actualDamage).coerceAtMost(currentRealMaxHealth)
                                } else {
                                    (lastRealHealthValue - actualDamage).coerceAtLeast(0f)
                                }

                                // Element of whatever last hit this entity, so the number can be coloured
                                // per element. This handler is a health differ and cannot know; the
                                // element engine records it on the damage seam. A heal has no element.
                                val elementOrdinal =
                                    if (isHeal) ElementType.NONE_ORDINAL
                                    else ElementCombat.lastElementOrdinal(entity)

                                // Split display: emit the physical and elemental halves as their own
                                // numbers. Only for damage (a heal has no components) and only when the
                                // accumulated composition actually covers this delta — otherwise fall
                                // through to the single merged number, which is what the health differ
                                // is good at.
                                val split = if (isHeal) null else composition.remove(uuid)
                                if (split != null &&
                                    (split.physical > 0.001f || split.elementalTotal() > 0.001f)
                                ) {
                                    // The components come from the damage seam, i.e. they are *pre-clamp*
                                    // and can sum to more than the target had left; the health differ is
                                    // the only part of this handler that knows what the player actually
                                    // watched happen. So when they disagree the observed delta wins and
                                    // every component is scaled by the same factor — the ratio between
                                    // them (the whole point of the split) survives, the total stops
                                    // lying (`cleanup47`, same reasoning as the deleted `originalDamage`).
                                    val componentTotal = split.physical + split.elementalTotal()
                                    val componentScale = if (componentTotal > actualDamage + 0.001f) {
                                        actualDamage / componentTotal
                                    } else {
                                        1f
                                    }
                                    val physical = split.physical * componentScale

                                    // `minHealthChange` is applied per component here, so a tiny
                                    // elemental tick can be hidden while the physical part still shows.
                                    if (physical > 0.001f && physical >= minChange) {
                                        sendToNearbyPlayers(
                                            entity,
                                            physical,
                                            false,
                                            entity.maxHealth,
                                            currentRealMaxHealth,
                                            afterRealHealth,
                                            ElementType.PHYSICAL.ordinal,
                                            PacketDamageIndicator.ROLE_PHYSICAL,
                                        )
                                    }

                                    // **One number per element.** A hit can carry several at once
                                    // (water conversion rider + lightning from `heavenly_punishment` +
                                    // their reaction shares), and merging them was what made the display
                                    // show two numbers instead of three.
                                    for ((ordinal, raw) in split.elemental) {
                                        val value = raw * componentScale
                                        if (value <= 0.001f || value < minChange) continue
                                        sendToNearbyPlayers(
                                            entity,
                                            value,
                                            false,
                                            entity.maxHealth,
                                            currentRealMaxHealth,
                                            afterRealHealth,
                                            ordinal,
                                            PacketDamageIndicator.ROLE_ELEMENTAL,
                                        )
                                    }
                                } else {
                                    sendToNearbyPlayers(
                                        entity,
                                        actualDamage,
                                        isHeal,
                                        entity.maxHealth,
                                        currentRealMaxHealth,
                                        afterRealHealth,
                                        elementOrdinal,
                                        PacketDamageIndicator.ROLE_SINGLE,
                                    )
                                }
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

        // Composition is per-tick state. When the split option is off nothing consumes it, so clearing
        // here is what keeps it from growing; when it is on, anything left belongs to an entity that
        // took damage but produced no visible delta (already at 0 HP, or outside render range) and is
        // equally stale by next tick.
        composition.keys.removeAll { it !in aliveUUIDs }
        if (!DamageIndicatorConfig.splitElemental) composition.clear()
        suppressedEntities.removeAll { it !in aliveUUIDs }
    }

    /** 1.12.2 `sendToNearbyPlayers` — everyone within 64 blocks, including the target itself. */
    private fun sendToNearbyPlayers(
        target: LivingEntity,
        actualDamage: Float,
        isHeal: Boolean,
        maxHealth: Float,
        realMaxHealth: Float,
        afterRealHealth: Float,
        elementOrdinal: Int,
        role: Int,
    ) {
        val players = target.world.getNonSpectatingEntities(
            ServerPlayerEntity::class.java,
            target.boundingBox.expand(64.0),
        )
        for (player in players) {
            val packet = PacketDamageIndicator(
                target.id,
                actualDamage,
                isHeal,
                player === target,
                maxHealth,
                realMaxHealth,
                afterRealHealth,
                elementOrdinal,
                role,
            )
            NetworkManager.sendToClient(packet, player)
        }
    }
}
