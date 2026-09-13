package dev.firefly.simplemod.damageindicator

import dev.firefly.simplemod.core.Listenable
import dev.firefly.simplemod.core.config.DamageIndicatorConfig
import dev.firefly.simplemod.network.NetworkManager
import dev.firefly.simplemod.util.isClientSide
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraftforge.common.DimensionManager
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.entity.living.LivingHurtEvent
import net.minecraftforge.fml.common.FMLCommonHandler
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase
import java.util.*

object DamageIndicatorHandler : Listenable {
    private val lastRealHealth = mutableMapOf<UUID, Float>()
    private val lastOriginalDamage = mutableMapOf<UUID, Float>()
    private val lastDamageTime = mutableMapOf<UUID, Long>()

    override fun init() {
        MinecraftForge.EVENT_BUS.register(this)
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onServerTick(event: TickEvent.ServerTickEvent) {
        if (event.phase != Phase.END) return
        if (!DamageIndicatorConfig.enabled) return
        if (FMLCommonHandler.instance().minecraftServerInstance == null) return
        val maxDist = DamageIndicatorConfig.maxDistance.toDouble()
        val now = System.currentTimeMillis()

        val aliveUUIDs = HashSet<UUID>()
        val processedThisTick = HashSet<UUID>()

        for (world in DimensionManager.getWorlds()) {
            if (world == null) continue

            val playersInWorld = world.playerEntities
            if (playersInWorld.isEmpty()) continue

            for (player in playersInWorld) {
                val box = player.entityBoundingBox.grow(maxDist, maxDist, maxDist)
                val entities = world.getEntitiesWithinAABB(EntityLivingBase::class.java, box)

                for (entity in entities) {
                    val uuid = entity.uniqueID
                    aliveUUIDs.add(uuid)

                    if (!processedThisTick.add(uuid)) continue

                    val currentRealHealth = entity.health + entity.absorptionAmount
                    val currentRealMaxHealth = entity.maxHealth + entity.absorptionAmount
                    val lastRealHealthValue = lastRealHealth[uuid]

                    if (lastRealHealthValue != null) {
                        val diff = currentRealHealth - lastRealHealthValue

                        if (kotlin.math.abs(diff) > 0.001f) {
                            val isHeal = diff > 0
                            val amount = kotlin.math.abs(diff)

                            val originalDamage = lastOriginalDamage[uuid]
                            val damageTime = lastDamageTime[uuid]
                            val recentDamage = damageTime != null && now - damageTime < 200

                            val finalOriginalDamage = if (recentDamage && originalDamage != null) {
                                originalDamage
                            } else {
                                amount
                            }

                            val actualDamage =
                                if (isHeal) amount else amount.coerceAtMost(lastRealHealthValue)
                            val isOverkill = !isHeal && finalOriginalDamage > lastRealHealthValue

                            val afterRealHealth = if (isHeal) {
                                (lastRealHealthValue + actualDamage)
                                    .coerceAtMost(currentRealMaxHealth)
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
                                afterRealHealth
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

        lastRealHealth.keys.removeAll { it !in aliveUUIDs }
        lastOriginalDamage.keys.removeAll { it !in aliveUUIDs }
        lastDamageTime.keys.removeAll { it !in aliveUUIDs }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onLivingHurt(event: LivingHurtEvent) {
        if (!DamageIndicatorConfig.enabled) return
        if (event.isClientSide) return

        val target = event.entityLiving
        val uuid = target.uniqueID

        lastOriginalDamage[uuid] = event.amount
        lastDamageTime[uuid] = System.currentTimeMillis()
    }

    private fun sendToNearbyPlayers(
        target: EntityLivingBase,
        actualDamage: Float,
        originalDamage: Float,
        isHeal: Boolean,
        isOverkill: Boolean,
        maxHealth: Float,
        realMaxHealth: Float,
        afterRealHealth: Float
    ) {
        val players = target.world.getEntitiesWithinAABB(
            EntityPlayerMP::class.java,
            target.entityBoundingBox.grow(64.0)
        )
        for (player in players) {
            val packet = PacketDamageIndicator(
                target,
                actualDamage,
                originalDamage,
                isHeal,
                isOverkill,
                player === target,
                maxHealth,
                realMaxHealth,
                afterRealHealth
            )
            NetworkManager.sendToClient(packet, player)
        }
    }
}