package dev.firefly.simplemod.damageindicator

import dev.firefly.simplemod.core.Listenable
import dev.firefly.simplemod.core.config.DamageIndicatorConfig
import dev.firefly.simplemod.network.NetworkManager
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayerMP
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

        val server = FMLCommonHandler.instance().minecraftServerInstance ?: return
        val world = server.entityWorld ?: return

        val playerList = server.playerList.players
        if (playerList.isEmpty()) return

        val centerPlayer = playerList[0]
        val maxDist = DamageIndicatorConfig.maxDistance.toDouble()

        val box = centerPlayer.entityBoundingBox.grow(maxDist, maxDist, maxDist)
        val entities = world.getEntitiesWithinAABB(EntityLivingBase::class.java, box)

        val now = System.currentTimeMillis()

        for (entity in entities) {
            val uuid = entity.uniqueID
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

                    if (damageTime != null && now - damageTime < 200) {
                        lastOriginalDamage.remove(uuid)
                        lastDamageTime.remove(uuid)
                    }

                    val finalOriginalDamage = if (originalDamage != null && now - (damageTime ?: 0) < 200) {
                        originalDamage
                    } else {
                        amount
                    }

                    val isSelf = entity == centerPlayer

                    val actualDamage = if (isHeal) amount else amount.coerceAtMost(lastRealHealthValue)
                    val isOverkill = !isHeal && finalOriginalDamage > lastRealHealthValue

                    val afterRealHealth = if (isHeal) {
                        (lastRealHealthValue + actualDamage).coerceAtMost(currentRealMaxHealth)
                    } else {
                        (lastRealHealthValue - actualDamage).coerceAtLeast(0f)
                    }

                    val packet = PacketDamageIndicator(
                        entity,
                        actualDamage,
                        finalOriginalDamage,
                        isHeal,
                        isOverkill,
                        isSelf,
                        entity.maxHealth,
                        currentRealMaxHealth,
                        afterRealHealth
                    )

                    sendToNearbyPlayers(entity, packet)
                    lastRealHealth[uuid] = currentRealHealth
                }
            }

            lastRealHealth[uuid] = currentRealHealth
        }

        val aliveUUIDs = entities.map { it.uniqueID }.toSet()
        lastRealHealth.keys.removeAll { !aliveUUIDs.contains(it) }
        lastOriginalDamage.keys.removeAll { !aliveUUIDs.contains(it) }
        lastDamageTime.keys.removeAll { !aliveUUIDs.contains(it) }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onLivingHurt(event: LivingHurtEvent) {
        if (!DamageIndicatorConfig.enabled) return
        if (event.entity.world.isRemote) return

        val target = event.entityLiving
        val uuid = target.uniqueID

        lastOriginalDamage[uuid] = event.amount
        lastDamageTime[uuid] = System.currentTimeMillis()
    }

    private fun sendToNearbyPlayers(target: EntityLivingBase, packet: PacketDamageIndicator) {
        val players = target.world.getEntitiesWithinAABB(
            EntityPlayerMP::class.java,
            target.entityBoundingBox.grow(64.0)
        )
        for (player in players) {
            NetworkManager.sendToClient(packet, player)
        }
    }
}