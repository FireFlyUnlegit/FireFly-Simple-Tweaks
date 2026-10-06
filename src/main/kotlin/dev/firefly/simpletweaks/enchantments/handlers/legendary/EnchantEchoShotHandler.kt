package dev.firefly.simpletweaks.enchantments.handlers.legendary

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.legendary.EnchantEchoShot
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.runPlayerAttack
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.projectile.EntityArrow
import net.minecraft.util.EnumParticleTypes
import net.minecraft.world.WorldServer
import net.minecraftforge.event.entity.living.LivingHurtEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.util.*

object EnchantEchoShotHandler : Listenable {

    private class DelayedEcho(
        val targetUUID: UUID,
        val ownerUUID: UUID,
        val dimension: Int,
        val damage: Float,
        var ticksLeft: Int,
    )

    private val echoes = mutableListOf<DelayedEcho>()

    @SubscribeEvent(priority = EventPriority.LOW)
    fun onHurt(e: LivingHurtEvent) {
        val arrow = e.source.immediateSource as? EntityArrow ?: return
        val shooter = arrow.shootingEntity as? EntityPlayer ?: return
        val target = e.entityLiving ?: return
        if (target === shooter) return

        val lvl = getItemSpecificEnchantLevel(shooter.heldItemMainhand, EnchantEchoShot)
        if (lvl <= 0) return

        val ratio = 0.35f + 0.07f * lvl
        val delay = (11 - lvl).coerceAtLeast(6)

        echoes.add(
            DelayedEcho(
                targetUUID = target.uniqueID,
                ownerUUID = shooter.uniqueID,
                dimension = target.dimension,
                damage = e.amount * ratio,
                ticksLeft = delay,
            )
        )
    }

    @SubscribeEvent
    fun onWorldTick(e: TickEvent.WorldTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        val world = e.world
        if (world.isRemote) return
        if (echoes.isEmpty()) return

        val currentDim = world.provider.dimension

        val toFire = mutableListOf<DelayedEcho>()
        val iter = echoes.iterator()
        while (iter.hasNext()) {
            val echo = iter.next()
            if (echo.dimension != currentDim) continue
            echo.ticksLeft--
            if (echo.ticksLeft <= 0) {
                toFire.add(echo)
                iter.remove()
            }
        }

        if (toFire.isEmpty()) return

        for (echo in toFire) {
            var target: EntityLivingBase? = null
            for (entity in world.loadedEntityList) {
                if (entity is EntityLivingBase && entity.uniqueID == echo.targetUUID) {
                    target = entity
                    break
                }
            }
            if (target == null || !target.isEntityAlive) continue

            val owner = world.getPlayerEntityByUUID(echo.ownerUUID) ?: continue

            target.runPlayerAttack(
                attacker = owner,
                rawDamage = echo.damage,
                forceHit = true,
                triggerEvent = true,
                allowCrit = false,
            )

            if (world is WorldServer) {
                world.spawnParticle(
                    EnumParticleTypes.CRIT_MAGIC,
                    target.posX,
                    target.posY + target.height * 0.6,
                    target.posZ,
                    12, 0.3, 0.3, 0.3, 0.1,
                )
                world.spawnParticle(
                    EnumParticleTypes.SPELL_WITCH,
                    target.posX,
                    target.posY + target.height * 0.6,
                    target.posZ,
                    6, 0.3, 0.3, 0.3, 0.05,
                )
            }
        }
    }
}