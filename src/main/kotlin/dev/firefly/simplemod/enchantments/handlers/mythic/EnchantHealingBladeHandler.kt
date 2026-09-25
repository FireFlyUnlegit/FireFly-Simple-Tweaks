package dev.firefly.simplemod.enchantments.handlers.mythic

import dev.firefly.simplemod.core.Listenable
import dev.firefly.simplemod.enchantments.mythic.EnchantHealingBlade
import dev.firefly.simplemod.util.attacker
import dev.firefly.simplemod.util.getItemSpecificEnchantLevel
import dev.firefly.simplemod.util.invalid
import dev.firefly.simplemod.util.target
import net.minecraft.entity.player.EntityPlayer
import net.minecraftforge.event.entity.living.LivingDamageEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.PlayerEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.util.*

object EnchantHealingBladeHandler : Listenable {

    private val absorptionCounter = mutableMapOf<UUID, Float>()
    private val absorptionAliveTimer = mutableMapOf<UUID, Int>()
    private val healingPool = mutableMapOf<UUID, Float>()

    @SubscribeEvent(priority = EventPriority.LOW)
    fun onLivingDamage(e: LivingDamageEvent) {
        if (e.invalid) return
        val attacker = e.attacker ?: return
        val id = attacker.uniqueID

        val level = getItemSpecificEnchantLevel(attacker.heldItemMainhand, EnchantHealingBlade)
        if (level <= 0) return

        val dmg = e.amount
        val healing = dmg * (0.1f * level) + (e.target.health * 0.0125f * level) + (healingPool[id] ?: 0f)
        if (healing <= 0f) return

        healingPool[id] = (healingPool[id] ?: 0f) + healing * 0.05f

        val diff = attacker.maxHealth - attacker.health
        if (healing <= diff) {
            attacker.heal(healing)
        } else {
            attacker.heal(diff)
            val maxAbsorption = attacker.maxHealth * (0.2f + level * 0.1f)
            val capacity = maxAbsorption - (absorptionCounter[id] ?: 0f)
            if (capacity > 0f) {
                val added = (healing - diff).coerceAtMost(capacity)
                attacker.absorptionAmount += added
                absorptionCounter[id] = (absorptionCounter[id] ?: 0f) + added
                absorptionAliveTimer[id] = 20 * level
            }
        }

        e.amount += healing
    }

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.invalid) return
        val p = e.player
        if (p.world.isRemote) return

        val id = p.uniqueID

        val pool = healingPool[id]
        if (pool != null) {
            val next = pool - pool * 0.05f
            if (next > 0.01f) healingPool[id] = next else healingPool.remove(id)
        }

        val tracked = absorptionCounter[id] ?: return
        val current = p.absorptionAmount

        if (current < tracked) {
            if (current <= 0f) {
                absorptionCounter.remove(id)
                absorptionAliveTimer.remove(id)
            } else {
                absorptionCounter[id] = current
            }
            return
        }

        val timer = absorptionAliveTimer[id] ?: run {
            absorptionCounter.remove(id)
            return
        }

        val remaining = timer - 1
        if (remaining <= 0) {
            p.absorptionAmount = (current - tracked).coerceAtLeast(0f)
            absorptionCounter.remove(id)
            absorptionAliveTimer.remove(id)
        } else {
            absorptionAliveTimer[id] = remaining
        }
    }

    @SubscribeEvent
    fun onLogout(e: PlayerEvent.PlayerLoggedOutEvent) {
        val id = e.player.uniqueID
        absorptionCounter.remove(id)
        absorptionAliveTimer.remove(id)
        healingPool.remove(id)
        clearContribution(e.player)
    }


    @SubscribeEvent
    fun onLogin(e: PlayerEvent.PlayerLoggedInEvent) {
        clearContribution(e.player)
    }

    @SubscribeEvent
    fun onClone(e: net.minecraftforge.event.entity.player.PlayerEvent.Clone) {
        clearContribution(e.original)
        clearContribution(e.entityPlayer)
    }

    private fun clearContribution(p: EntityPlayer) {
        val id = p.uniqueID
        val tracked = absorptionCounter[id] ?: 0f
        if (tracked > 0f) {
            p.absorptionAmount = (p.absorptionAmount - tracked).coerceAtLeast(0f)
        }
        absorptionCounter.remove(id)
        absorptionAliveTimer.remove(id)
        healingPool.remove(id)
    }
}