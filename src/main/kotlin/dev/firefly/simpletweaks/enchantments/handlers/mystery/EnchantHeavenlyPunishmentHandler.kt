package dev.firefly.simpletweaks.enchantments.handlers.mystery

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.mystery.EnchantHeavenlyPunishment
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.invalid
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.SharedMonsterAttributes
import net.minecraft.entity.ai.attributes.AttributeModifier
import net.minecraft.entity.ai.attributes.IAttributeInstance
import net.minecraft.entity.effect.EntityLightningBolt
import net.minecraft.init.SoundEvents
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumParticleTypes
import net.minecraft.util.SoundCategory
import net.minecraft.world.World
import net.minecraft.world.WorldServer
import net.minecraftforge.event.entity.living.LivingDamageEvent
import net.minecraftforge.event.entity.living.LivingEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import java.util.*

object EnchantHeavenlyPunishmentHandler : Listenable {

    private const val NBT_WEAK_UNTIL   = "st_hp_weak_until"
    private const val NBT_WEAK_AMOUNT  = "st_hp_weak_amount"
    private const val NBT_WEAK_OWNER   = "st_hp_weak_owner"
    private const val NBT_FREEZE_UNTIL = "st_hp_freeze_until"
    private const val NBT_LAST_FREEZE  = "st_hp_last_freeze"

    private val ATK_SPEED_UUID: UUID =
        UUID.nameUUIDFromBytes("simple_tweaks_heavenly_punishment_atk_speed".toByteArray())

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onLivingDamage(event: LivingDamageEvent) {
        if (event.invalid) return

        val target = event.entityLiving ?: return
        val world: World = target.world ?: return
        if (world.isRemote) return

        val source = event.source ?: return
        val attacker = source.trueSource
        val now = world.totalWorldTime

        if (attacker is EntityLivingBase && attacker !== target) {
            val level = getItemSpecificEnchantLevel(
                attacker.heldItemMainhand,
                EnchantHeavenlyPunishment
            )
            if (level > 0) {
                event.amount *= 1.0f * level + 1f

                val data: NBTTagCompound = target.entityData
                data.setLong(NBT_WEAK_UNTIL, now + level * 10)
                data.setFloat(NBT_WEAK_AMOUNT, 0.225f * level)
                data.setString(NBT_WEAK_OWNER, attacker.uniqueID.toString())

                val lastFreeze = data.getLong(NBT_LAST_FREEZE)
                if (now - lastFreeze >= 20) {
                    data.setLong(NBT_FREEZE_UNTIL, now + 10)
                    data.setLong(NBT_LAST_FREEZE, now)
                }

                spawnLightningVisual(world, target)
            }
        }

        if (attacker is EntityLivingBase) {
            val data: NBTTagCompound = attacker.entityData
            val until = data.getLong(NBT_WEAK_UNTIL)
            if (until > 0L && until > now) {
                val owner = data.getString(NBT_WEAK_OWNER)
                if (owner.isNotEmpty() && owner == target.uniqueID.toString()) {
                    val reduce = data.getFloat(NBT_WEAK_AMOUNT)
                    if (reduce > 0f) {
                        event.amount *= (1f - reduce)
                    }
                }
            }
        }
    }

    @SubscribeEvent
    fun onLivingUpdate(event: LivingEvent.LivingUpdateEvent) {
        val entity = event.entityLiving ?: return
        if (entity.world.isRemote) return

        val data: NBTTagCompound = entity.entityData
        val now = entity.world.totalWorldTime

        val freezeUntil = data.getLong(NBT_FREEZE_UNTIL)
        if (freezeUntil > 0L) {
            if (freezeUntil > now) {
                entity.motionX = 0.0
                entity.motionY = 0.0
                entity.motionZ = 0.0
                applyAttackSpeedSlow(entity)
            } else {
                data.removeTag(NBT_FREEZE_UNTIL)
            }
        }

        val weakUntil = data.getLong(NBT_WEAK_UNTIL)
        if (weakUntil > 0L && weakUntil <= now) {
            removeAttackSpeedSlow(entity)
            data.removeTag(NBT_WEAK_UNTIL)
            data.removeTag(NBT_WEAK_AMOUNT)
            data.removeTag(NBT_WEAK_OWNER)
        }
    }

    private fun applyAttackSpeedSlow(entity: EntityLivingBase) {
        val attr: IAttributeInstance? = entity.getEntityAttribute(SharedMonsterAttributes.ATTACK_SPEED)
        if (attr == null) return
        attr.removeModifier(ATK_SPEED_UUID)
        val modifier = AttributeModifier(
            ATK_SPEED_UUID,
            "Heavenly Punishment Attack Speed Slow",
            -100.0,
            0
        )
        modifier.setSaved(false)
        attr.applyModifier(modifier)
    }
    private fun removeAttackSpeedSlow(entity: EntityLivingBase) {
        val attr: IAttributeInstance? = entity.getEntityAttribute(SharedMonsterAttributes.ATTACK_SPEED)
        attr?.removeModifier(ATK_SPEED_UUID)
    }

    private fun spawnLightningVisual(world: World, target: EntityLivingBase) {
        val bolt = EntityLightningBolt(world, target.posX, target.posY, target.posZ, true)
        world.addWeatherEffect(bolt)

        if (world is WorldServer) {
            world.spawnParticle(
                EnumParticleTypes.CRIT_MAGIC,
                target.posX, target.posY + target.height / 2.0, target.posZ,
                20, 0.5, 0.5, 0.5, 0.15
            )
            world.spawnParticle(
                EnumParticleTypes.END_ROD,
                target.posX, target.posY + target.height / 2.0, target.posZ,
                10, 0.3, 0.3, 0.3, 0.05
            )
        }

        world.playSound(
            null, target.posX, target.posY, target.posZ,
            SoundEvents.ENTITY_LIGHTNING_THUNDER,
            SoundCategory.PLAYERS,
            0.8f, 1.2f
        )
    }
}