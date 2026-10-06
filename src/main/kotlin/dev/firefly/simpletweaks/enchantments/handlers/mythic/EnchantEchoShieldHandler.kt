package dev.firefly.simpletweaks.enchantments.handlers.mythic

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.mythic.EnchantEchoShield
import dev.firefly.simpletweaks.util.*
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.SoundEvents
import net.minecraft.util.EnumParticleTypes
import net.minecraft.util.SoundCategory
import net.minecraft.world.WorldServer
import net.minecraftforge.event.entity.living.*
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import java.util.*

object EnchantEchoShieldHandler : Listenable {

    private val echoBuffer = mutableMapOf<UUID, Float>()
    private const val NBT_NO_HEAL_UNTIL = "st_echo_no_heal_until"

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        val player = e.target as? EntityPlayer ?: return
        val attacker = e.attacker ?: return
        if (attacker === player) return

        if (e.amount.isNaN() || e.amount.isInfinite()) {
            println("[ST-NaN] EchoShield.onHurt: incoming amount is NaN/Inf, skip. amount=${e.amount}")
            return
        }

        val lvl = player.getArmorEnchantLevel(EnchantEchoShield, 20)
        if (lvl <= 0) return

        markNoHeal(attacker, lvl)

        val id = player.uniqueID
        val buffer = echoBuffer[id] ?: 0f

        if (buffer.isNaN() || buffer.isInfinite()) {
            println("[ST-NaN] EchoShield.onHurt: buffer is NaN/Inf, clearing. buffer=$buffer")
            echoBuffer.remove(id)
            return
        }
        if (buffer <= 0f) return

        if (buffer >= e.amount) {
            e.cancel()

            attacker.runPlayerAttack(player, buffer.coerceAtMost(attacker.maxHealth), ignoreArmorAndPotion = true)
            echoBuffer.remove(id)

            playFeedback(player, big = true)
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onDamage(e: LivingDamageEvent) {
        if (e.invalid) return
        val player = e.target as? EntityPlayer ?: return
        val attacker = e.attacker ?: return
        if (attacker === player) return

        if (e.amount.isNaN() || e.amount.isInfinite()) {
            println("[ST-NaN] EchoShield.onDamage: amount is NaN/Inf, skip. amount=${e.amount}")
            return
        }

        val lvl = player.getArmorEnchantLevel(EnchantEchoShield, 20)
        if (lvl <= 0) return

        markNoHeal(attacker, lvl)

        val id = player.uniqueID
        val ratio = 0.03f * lvl
        val reduced = e.amount * ratio

        if (reduced.isNaN() || reduced.isInfinite() || reduced <= 0f) return

        attacker.runPlayerAttack(player, reduced, ignoreArmorAndPotion = true)
        e.amount -= reduced

        val newBuffer = (echoBuffer[id] ?: 0f) + reduced
        if (!newBuffer.isNaN() && !newBuffer.isInfinite()) {
            echoBuffer[id] = newBuffer
        } else {
            println("[ST-NaN] EchoShield.onDamage: buffer overflow to NaN/Inf, reset to 0")
            echoBuffer.remove(id)
        }

        playFeedback(player, big = false)
    }

    @SubscribeEvent
    fun onHeal(e: LivingHealEvent) {
        val entity = e.entityLiving ?: return
        if (entity.world.isRemote) return
        val until = entity.entityData.getLong(NBT_NO_HEAL_UNTIL)
        if (until > 0L && entity.world.totalWorldTime < until) {
            e.isCanceled = true
        }
    }

    @SubscribeEvent
    fun onLivingUpdate(e: LivingEvent.LivingUpdateEvent) {
        val entity = e.entityLiving ?: return
        if (entity.world.isRemote) return
        val data = entity.entityData
        val until = data.getLong(NBT_NO_HEAL_UNTIL)
        if (until <= 0L) return
        if (entity.world.totalWorldTime > until) {
            data.removeTag(NBT_NO_HEAL_UNTIL)
        }
    }

    private fun markNoHeal(entity: EntityLivingBase, lvl: Int) {
        val now = entity.world.totalWorldTime
        val until = now + lvl
        val data = entity.entityData
        val prev = data.getLong(NBT_NO_HEAL_UNTIL)
        if (until > prev) {
            data.setLong(NBT_NO_HEAL_UNTIL, until)
        }
    }

    @SubscribeEvent
    fun onDeath(e: LivingDeathEvent) {
        val player = e.entityLiving as? EntityPlayer ?: return
        echoBuffer.remove(player.uniqueID)
    }

    @SubscribeEvent
    fun onClone(e: PlayerEvent.Clone) {
        if (!e.isWasDeath) return
        echoBuffer.remove(e.original.uniqueID)
    }

    @SubscribeEvent
    fun onLogout(e: net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent) {
        echoBuffer.remove(e.player.uniqueID)
    }

    private fun playFeedback(player: EntityPlayer, big: Boolean) {
        val world = player.world as? WorldServer ?: return
        if (big) {
            world.spawnParticle(EnumParticleTypes.EXPLOSION_LARGE, player.posX, player.posY + 1.0, player.posZ, 1, 0.0, 0.0, 0.0, 0.0)
            world.spawnParticle(EnumParticleTypes.CRIT_MAGIC, player.posX, player.posY + 1.0, player.posZ, 30, 0.5, 0.5, 0.5, 0.1)
            world.playSound(null, player.posX, player.posY, player.posZ, SoundEvents.ENTITY_LIGHTNING_THUNDER, SoundCategory.PLAYERS, 0.6f, 1.4f)
        } else {
            world.spawnParticle(EnumParticleTypes.ENCHANTMENT_TABLE, player.posX, player.posY + 1.0, player.posZ, 6, 0.4, 0.4, 0.4, 0.02)
        }
    }
}