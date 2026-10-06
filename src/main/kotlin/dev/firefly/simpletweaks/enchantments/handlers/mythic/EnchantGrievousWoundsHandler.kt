package dev.firefly.simpletweaks.enchantments.handlers.mythic

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.mythic.EnchantGrievousWounds
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.EnumParticleTypes
import net.minecraft.world.WorldServer
import net.minecraftforge.event.entity.living.LivingHealEvent
import net.minecraftforge.event.entity.living.LivingHurtEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.lang.ref.WeakReference
import java.util.*

object EnchantGrievousWoundsHandler : Listenable {

    private class WoundState(val ref: WeakReference<EntityLivingBase>) {
        var expireAtMs: Long = 0
        var ratio: Float = 0f
    }

    private val wounds = mutableMapOf<UUID, WoundState>()


    @SubscribeEvent
    fun onHurt(e: LivingHurtEvent) {
        val target = e.entityLiving ?: return
        val attacker = e.source.trueSource as? EntityPlayer ?: return
        if (attacker === target) return

        val lvl = getItemSpecificEnchantLevel(attacker.heldItemMainhand, EnchantGrievousWounds)
        if (lvl <= 0) return

        val ratio = (0.2f * lvl).coerceAtMost(1.0f)
        val duration = 1000L + 250L * lvl
        val now = System.currentTimeMillis()

        val id = target.uniqueID
        val state = wounds[id]
        if (state == null || state.ref.get() !== target) {
            wounds[id] = WoundState(WeakReference(target)).also {
                it.expireAtMs = now + duration
                it.ratio = ratio
            }
        } else {
            if (ratio > state.ratio) state.ratio = ratio
            state.expireAtMs = now + duration
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onHeal(e: LivingHealEvent) {
        val target = e.entityLiving ?: return
        val state = wounds[target.uniqueID] ?: return
        if (state.ref.get() !== target) {
            wounds.remove(target.uniqueID)
            return
        }
        if (System.currentTimeMillis() > state.expireAtMs) {
            wounds.remove(target.uniqueID)
            return
        }

        val ratio = state.ratio
        if (ratio >= 1f) {
            e.amount = 0f
            e.isCanceled = true
        } else {
            e.amount = e.amount * (1f - ratio)
        }
    }

    @SubscribeEvent
    fun onWorldTick(e: TickEvent.WorldTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        val world = e.world
        if (world.isRemote) return
        if (wounds.isEmpty()) return

        val now = System.currentTimeMillis()
        val iter = wounds.entries.iterator()
        while (iter.hasNext()) {
            val (_, state) = iter.next()
            val entity = state.ref.get()
            if (entity == null || entity.isDead || !entity.isEntityAlive) {
                iter.remove()
                continue
            }
            if (now > state.expireAtMs) {
                iter.remove()
                continue
            }
            if (entity.ticksExisted % 10 == 0 && world is WorldServer) {
                world.spawnParticle(
                    EnumParticleTypes.SPELL_WITCH,
                    entity.posX,
                    entity.posY + entity.height * 0.8,
                    entity.posZ,
                    3, 0.3, 0.3, 0.3, 0.0,
                )
            }
        }
    }
}