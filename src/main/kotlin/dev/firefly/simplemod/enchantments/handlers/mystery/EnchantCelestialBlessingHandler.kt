package dev.firefly.simplemod.enchantments.handlers.mystery

import dev.firefly.simplemod.core.Listenable
import dev.firefly.simplemod.enchantments.mystery.EnchantCelestialBlessing
import dev.firefly.simplemod.util.*
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.SoundEvents
import net.minecraft.util.SoundCategory
import net.minecraftforge.event.entity.living.LivingDamageEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.PlayerEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.util.*
import kotlin.math.roundToInt

object EnchantCelestialBlessingHandler : Listenable {
    val manaPool = mutableMapOf<UUID, Float>()
    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onEntityDamage(e: LivingDamageEvent) {
        if (e.invalid) return
        val p = e.attacker?: return
        val t = e.entityLiving?: return
        val pid = p.uniqueID
        val tid = t.uniqueID
        val atklvl = getItemSpecificEnchantLevel(p.heldItemMainhand, EnchantCelestialBlessing)
        val reclvl = getItemSpecificEnchantLevel(t.heldItemMainhand, EnchantCelestialBlessing)
        if (atklvl > 0) {
            val healing = (e.amount * 0.25f * atklvl)
            if (p.lostHealth <= healing) manaPool[pid] = (manaPool[pid]?: 0f) + healing - p.lostHealth
            p.heal(healing)
            val extraDMG =healing * (p.lostHealthRatio + 1f) + (t.health * (0.05 + 0.02 * atklvl)).toFloat()
            val healthReduction = (((manaPool[pid]?: 0f) * 0.05f * atklvl).coerceAtMost(t.lostHealth * 0.05f)).coerceAtMost(t.health - 1)
            e.amount += extraDMG
            t.health -= healthReduction
            t.hurtResistantTime=0
            val maxHealthAttr = t.getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.MAX_HEALTH)
            if (maxHealthAttr != null) {
                val modifierUUID = UUID.nameUUIDFromBytes("simplemod_celestial_shred".toByteArray())

                val reduction = (e.amount + healthReduction) * 0.1f * atklvl

                val existingModifier = maxHealthAttr.getModifier(modifierUUID)

                if (existingModifier != null) {
                    maxHealthAttr.removeModifier(existingModifier)
                }

                val currentReduction = existingModifier?.amount ?: 0.0
                val newReduction = currentReduction - reduction

                val maxPossibleReduction = -(maxHealthAttr.baseValue - 1.0)
                val safeReduction = newReduction.coerceAtLeast(maxPossibleReduction)

                val newModifier = net.minecraft.entity.ai.attributes.AttributeModifier(
                    modifierUUID,
                    "Celestial Blessing Shred",
                    safeReduction,
                    0
                )
                maxHealthAttr.applyModifier(newModifier)
                if (t.health > t.maxHealth) {
                    t.health = t.maxHealth
                }
            }
        }
        if (reclvl > 0) {
            val defenseFactor = 1.0f - 0.12f * reclvl
            val incomingDamage = e.amount * defenseFactor
            val currentMana = manaPool[tid] ?: 0f

            if (incomingDamage <= currentMana) {
                e.cancel()
                t.hurtResistantTime+=10

                (t as? EntityPlayer)?.addExperience(incomingDamage.roundToInt())
                manaPool[tid] = currentMana - incomingDamage
                if (e.entityLiving is EntityPlayer) {
                    e.entityLiving.world.playSound(
                        e.entityLiving as EntityPlayer,
                        e.entityLiving.posX,
                        e.entityLiving.posY,
                        e.entityLiving.posZ,
                        SoundEvents.BLOCK_ANVIL_USE,
                        SoundCategory.PLAYERS,
                        1f,
                        1f,
                    )
                }
            } else if (currentMana > 0f) {
                val overflowDamage = (incomingDamage - currentMana) / defenseFactor
                (t as? EntityPlayer)?.addExperience((manaPool[tid]?: 0f).roundToInt())
                e.amount = overflowDamage
                t.hurtResistantTime+=10

                manaPool[tid] = 0f
            }
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.invalid) return
        val id = e.player.uniqueID
        var currentMana = manaPool[id] ?: 0f
        val reclvl = getItemSpecificEnchantLevel(e.player.heldItemMainhand, EnchantCelestialBlessing)

        if (currentMana > 1f && reclvl > 0) {
            if (e.player.foodStats.foodLevel < 20) {
                currentMana -= 0.5f
                e.player.addExperience(1)
                e.player.foodStats.foodLevel++
            }
            if (e.player.health < e.player.maxHealth) {
                val healingCount = if (e.player.lostHealth > currentMana) currentMana else e.player.lostHealth
                currentMana -= healingCount
                e.player.heal(healingCount)
            }
        }

        if (currentMana > 0f && reclvl > 0 && e.player.ticksExisted % 4 == 0) {
            e.player.world.spawnRingParticles(
                net.minecraft.util.EnumParticleTypes.END_ROD,
                e.player.posX, e.player.posY + 1.0, e.player.posZ,
                1.0,
                4,
                e.player.ticksExisted * 0.5,
                0.0,
                0.0
            )
        }

        if (e.player.ticksExisted % 20 == 0) {
            if (currentMana > 1f) {
                e.player.addExperience((currentMana * 0.05f).roundToInt())
                currentMana *= 0.95f
            } else {
                manaPool.remove(id)
                return
            }
        }

        if (manaPool.containsKey(id)) {
            manaPool[id] = currentMana
        }
    }
    @SubscribeEvent
    fun onLogout(e: PlayerEvent.PlayerLoggedOutEvent) {
        manaPool.remove(e.player.uniqueID)
    }
}