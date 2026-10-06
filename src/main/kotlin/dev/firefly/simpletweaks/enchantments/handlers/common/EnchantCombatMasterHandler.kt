package dev.firefly.simpletweaks.enchantments.handlers.common

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.common.EnchantCombatMaster
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.invalid
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.event.entity.living.LivingDamageEvent
import net.minecraftforge.event.entity.living.LivingEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object EnchantCombatMasterHandler : Listenable {
    private const val NBT_COMBO_TARGET   = "st_combo_target"
    private const val NBT_COMBO_STACKS   = "st_combo_stacks"   
    private const val NBT_COMBO_LAST_HIT = "st_combo_last_hit" 
    private const val MAX_STACKS = 200

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onLivingDamage(event: LivingDamageEvent) {
        if (event.invalid) return

        val target = event.entityLiving ?: return
        val world = target.world ?: return
        if (world.isRemote) return

        val source = event.source ?: return
        val attacker = source.trueSource
        val now = world.totalWorldTime

        if (target is EntityPlayer) {
            val data = target.entityData
            if (data.getInteger(NBT_COMBO_STACKS) > 0) {
                resetCombo(data)
            }
        }

        if (attacker is EntityLivingBase && attacker !== target) {
            val level = getItemSpecificEnchantLevel(
                attacker.heldItemMainhand,
                EnchantCombatMaster
            )
            if (level > 0) {
                val data = attacker.entityData
                val prevTarget = data.getString(NBT_COMBO_TARGET)
                val targetUUID = target.uniqueID.toString()

                val stacks = if (prevTarget == targetUUID) {
                    (data.getInteger(NBT_COMBO_STACKS) + 1).coerceAtMost(MAX_STACKS)
                } else {
                    1
                }
                data.setString(NBT_COMBO_TARGET, targetUUID)
                data.setInteger(NBT_COMBO_STACKS, stacks)
                data.setLong(NBT_COMBO_LAST_HIT, now)

                val bonus = (stacks * 0.02f * level)
                    .coerceAtMost(0.2f * level)
                event.amount *= (1f + bonus)
            }
        }
    }

    @SubscribeEvent
    fun onLivingUpdate(event: LivingEvent.LivingUpdateEvent) {
        val entity = event.entityLiving ?: return
        if (entity !is EntityPlayer) return
        if (entity.world.isRemote) return

        val data = entity.entityData
        if (data.getInteger(NBT_COMBO_STACKS) <= 0) return

        val lastHit = data.getLong(NBT_COMBO_LAST_HIT)
        if (lastHit <= 0L) return

        val now = entity.world.totalWorldTime
        if (now - lastHit > 100) {
            resetCombo(data)
        }
    }

    private fun resetCombo(data: NBTTagCompound) {
        data.setInteger(NBT_COMBO_STACKS, 0)
        data.removeTag(NBT_COMBO_TARGET)
        data.removeTag(NBT_COMBO_LAST_HIT)
    }
}