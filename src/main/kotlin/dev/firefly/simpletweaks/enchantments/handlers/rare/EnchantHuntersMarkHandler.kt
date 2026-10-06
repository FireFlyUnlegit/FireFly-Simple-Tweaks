package dev.firefly.simpletweaks.enchantments.handlers.rare

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.rare.EnchantHuntersMark
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.projectile.EntityArrow
import net.minecraft.util.EnumParticleTypes
import net.minecraft.world.WorldServer
import net.minecraftforge.event.entity.living.LivingHurtEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.lang.ref.WeakReference
import java.util.*

object EnchantHuntersMarkHandler : Listenable {
    private class MarkState(val ref: WeakReference<EntityLivingBase>) {
        var expireAtMs: Long = 0
        var lvl: Int = 0
    }

    private val marks = mutableMapOf<UUID, MarkState>()

    @SubscribeEvent(priority = EventPriority.LOW)
    fun onHurt(e: LivingHurtEvent) {
        val target = e.entityLiving ?: return
        val attacker = e.source.trueSource as? EntityPlayer ?: return
        if (attacker === target) return

        val arrow = e.source.immediateSource as? EntityArrow
        val isArrowHit = arrow != null && arrow.shootingEntity === attacker

        if (isArrowHit) {
            val lvl = getItemSpecificEnchantLevel(attacker.heldItemMainhand, EnchantHuntersMark)
            if (lvl <= 0) return
            applyMark(target, lvl)
            spawnMarkFx(target)
        } else {
            val state = marks[target.uniqueID] ?: return
            if (state.ref.get() !== target) {
                marks.remove(target.uniqueID)
                return
            }
            if (System.currentTimeMillis() > state.expireAtMs) {
                marks.remove(target.uniqueID)
                return
            }

            e.amount *= 1f + 0.2f * state.lvl
            marks.remove(target.uniqueID)

            spawnConsumeFx(target)
        }
    }

    private fun applyMark(target: EntityLivingBase, lvl: Int) {
        val now = System.currentTimeMillis()
        val id = target.uniqueID
        val existing = marks[id]
        if (existing != null && existing.ref.get() === target) {
            if (lvl > existing.lvl) existing.lvl = lvl
            existing.expireAtMs = now + 10_000L
        } else {
            marks[id] = MarkState(WeakReference(target)).also {
                it.expireAtMs = now + 10_000L
                it.lvl = lvl
            }
        }
    }


    private fun spawnMarkFx(target: EntityLivingBase) {
        val world = target.world as? WorldServer ?: return
        world.spawnParticle(
            EnumParticleTypes.CRIT,
            target.posX,
            target.posY + target.height * 0.7,
            target.posZ,
            8, 0.4, 0.4, 0.4, 0.05,
        )
    }

    private fun spawnConsumeFx(target: EntityLivingBase) {
        val world = target.world as? WorldServer ?: return
        world.spawnParticle(
            EnumParticleTypes.CRIT_MAGIC,
            target.posX,
            target.posY + target.height * 0.5,
            target.posZ,
            15, 0.5, 0.5, 0.5, 0.1,
        )
    }

    @SubscribeEvent
    fun onWorldTick(e: TickEvent.WorldTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        val world = e.world
        if (world.isRemote) return
        if (marks.isEmpty()) return

        val now = System.currentTimeMillis()
        val iter = marks.entries.iterator()
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
            if (entity.ticksExisted % 20 == 0 && world is WorldServer) {
                world.spawnParticle(
                    EnumParticleTypes.CRIT,
                    entity.posX,
                    entity.posY + entity.height * 0.8,
                    entity.posZ,
                    2, 0.2, 0.2, 0.2, 0.0,
                )
            }
        }
    }
}