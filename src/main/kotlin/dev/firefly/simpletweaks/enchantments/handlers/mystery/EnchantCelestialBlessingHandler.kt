package dev.firefly.simpletweaks.enchantments.handlers.mystery

import dev.firefly.simpletweaks.client.ClientManaPoolCache
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.core.config.GeneralConfig
import dev.firefly.simpletweaks.enchantments.mystery.EnchantCelestialBlessing
import dev.firefly.simpletweaks.network.NetworkManager
import dev.firefly.simpletweaks.network.packets.PacketCelestialRing
import dev.firefly.simpletweaks.network.packets.PacketManaPoolSync
import dev.firefly.simpletweaks.util.*
import dev.firefly.simpletweaks.util.damagesource.CelestialDamageSources
import net.minecraft.entity.EntityCreature
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.EnumCreatureType
import net.minecraft.entity.SharedMonsterAttributes
import net.minecraft.entity.ai.attributes.AttributeModifier
import net.minecraft.entity.passive.EntityAnimal
import net.minecraft.entity.passive.EntityTameable
import net.minecraft.entity.passive.EntityVillager
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.init.SoundEvents
import net.minecraft.util.EnumParticleTypes
import net.minecraft.util.SoundCategory
import net.minecraft.world.World
import net.minecraft.world.WorldServer
import net.minecraftforge.event.entity.living.LivingDamageEvent
import net.minecraftforge.event.entity.player.AttackEntityEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.PlayerEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.lang.ref.WeakReference
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

object EnchantCelestialBlessingHandler : Listenable {

    val manaPool = mutableMapOf<UUID, Float>()

    private val targetQueue = mutableMapOf<UUID, MutableList<UUID>>()

    private val autoAttackCooldown = mutableMapOf<UUID, Int>()
    private val projectiles = mutableListOf<CelestialProjectile>()
    private val KNOCKBACK_UUID: UUID = UUID.nameUUIDFromBytes("simple_tweaks_celestial_knockback".toByteArray())
    private val ATTACK_SPEED_UUID: UUID = UUID.nameUUIDFromBytes("simple_tweaks_celestial_attack_speed".toByteArray())
    private val SHRED_UUID: UUID = UUID.nameUUIDFromBytes("simple_tweaks_celestial_shred".toByteArray())
    private val lastSyncedMana = mutableMapOf<UUID, Float>()
    private val lastSyncTick = mutableMapOf<UUID, Int>()

    /** 60 秒 = 1200 tick */
    private const val SHRED_DURATION_TICKS = 1200

    /** 记录被削减最大生命值的目标及其过期 tick */
    private class ShredState(val ref: WeakReference<EntityLivingBase>) {
        var expiryTick: Long = 0
    }
    private val shredStates = mutableMapOf<UUID, ShredState>()

    private fun EntityLivingBase.isFriendlyTo(player: EntityPlayer): Boolean {
        if (this == player) return true
        if (this is EntityPlayer) return true
        if (this is EntityTameable && this.isTamed && this.owner == player) return true
        if (this.ridingEntity == player) return true
        return false
    }

    private fun EntityLivingBase.isHostile(): Boolean {
        if (this.isCreatureType(EnumCreatureType.MONSTER, false)) return true
        if (this is net.minecraft.entity.EntityLiving && this.attackTarget != null) return true
        return false
    }

    private fun EntityLivingBase.isNeutral(player: EntityPlayer): Boolean {
        if (this is EntityPlayer) return false
        if (this.isFriendlyTo(player)) return false
        if (this.isHostile()) return false
        if (this is EntityAnimal) return false
        if (this is EntityVillager) return false
        if (this is EntityCreature) return true
        return false
    }

    private fun EntityLivingBase.isValidChainTarget(player: EntityPlayer): Boolean {
        return this.isEntityAlive && (this.isHostile() || this.isNeutral(player))
    }

    private data class CelestialProjectile(
        var x: Double, var y: Double, var z: Double,
        var targetUUID: UUID,
        val ownerUUID: UUID,
        val damage: Float,
        val level: Int = 0,
        var life: Int = 0,
        var remainingHits: Int = 0,
        var hitCooldown: Int = 0,
    )

    @SubscribeEvent
    fun onPlayerAttack(event: AttackEntityEvent) {
        val player = event.entityPlayer
        val target = event.target
        if (target is EntityLivingBase) {
            val atklvl = getItemSpecificEnchantLevel(player.heldItemMainhand, EnchantCelestialBlessing)
            if (atklvl > 0) {
                val queue = targetQueue.getOrPut(player.uniqueID) { mutableListOf() }
                if (!queue.contains(target.uniqueID)) {
                    queue.add(target.uniqueID)
                    if (queue.size > 32) queue.removeAt(0)
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onEntityDamage(e: LivingDamageEvent) {
        if (e.invalid) return
        val p = e.attacker ?: return
        val t = e.entityLiving ?: return
        val pid = p.uniqueID
        val tid = t.uniqueID
        val atklvl = getItemSpecificEnchantLevel(p.heldItemMainhand, EnchantCelestialBlessing)
        val reclvl = getItemSpecificEnchantLevel(t.heldItemMainhand, EnchantCelestialBlessing)

        if (atklvl > 0) {
            val healing = (e.amount * 0.18f * atklvl)
            val manaBoost = (e.amount * 0.025f * atklvl)
            val cost = ((manaPool[pid] ?: 0f) * 0.02f * atklvl).coerceAtMost(t.maxHealth * 2f)
            val extraDMG = healing + cost
            manaPool[pid] = (manaPool[pid] ?: 0f) + (healing + manaBoost - cost).coerceIn(0f, t.maxHealth * 2f)
            p.heal(healing)
            e.amount += extraDMG
            val reduction = (e.amount) * 0.04f * atklvl + 1f
            t.hurtResistantTime = 0

            val maxHealthAttr = t.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH)
            if (maxHealthAttr != null) {
                val now = t.world.totalWorldTime
                val state = shredStates[tid]
                if (state != null && now > state.expiryTick) {
                    maxHealthAttr.removeModifier(SHRED_UUID)
                    shredStates.remove(tid)
                }

                val existingModifier = maxHealthAttr.getModifier(SHRED_UUID)
                val currentReduction = existingModifier?.amount ?: 0.0
                val newReduction = currentReduction - reduction
                val maxPossibleReduction = -(maxHealthAttr.baseValue - 1.0)
                val safeReduction = newReduction.coerceAtLeast(maxPossibleReduction)

                if (existingModifier != null) maxHealthAttr.removeModifier(existingModifier)
                val newModifier = AttributeModifier(
                    SHRED_UUID, "Celestial Blessing Shred", safeReduction, 0
                )
                newModifier.setSaved(false)
                maxHealthAttr.applyModifier(newModifier)

                shredStates[tid] = ShredState(WeakReference(t)).also {
                    it.expiryTick = now + SHRED_DURATION_TICKS
                }

                if (t.health > t.maxHealth) t.health = t.maxHealth
            }
        }

        if (reclvl > 0) {
            val defenseFactor = 1.0f - 0.12f * reclvl
            val incomingDamage = e.amount * defenseFactor
            val currentMana = manaPool[tid] ?: 0f
            if (incomingDamage <= currentMana) {
                e.cancel()
                t.hurtResistantTime += reclvl * 4
                (t as? EntityPlayer)?.addExperience(incomingDamage.roundToInt())
                manaPool[tid] = currentMana - incomingDamage
                if (t is EntityPlayer) {
                    t.world.playSound(null, t.posX, t.posY, t.posZ, SoundEvents.BLOCK_ANVIL_USE, SoundCategory.PLAYERS, 1f, 1.2f)
                }
            } else if (currentMana > 0f) {
                val overflowDamage = (incomingDamage - currentMana) / defenseFactor
                (t as? EntityPlayer)?.addExperience(currentMana.roundToInt())
                e.amount = overflowDamage
                t.hurtResistantTime += reclvl * 4
                manaPool[tid] = 0f
            }
        }
    }

    @SubscribeEvent
    fun onServerTick(e: TickEvent.ServerTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        if (shredStates.isEmpty()) return

        val iter = shredStates.iterator()
        while (iter.hasNext()) {
            val (_, state) = iter.next()
            val entity = state.ref.get()
            if (entity == null || entity.isDead || !entity.isEntityAlive) {
                iter.remove()
                continue
            }
            val now = entity.world.totalWorldTime
            if (now > state.expiryTick) {
                entity.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH)
                    ?.removeModifier(SHRED_UUID)
                if (entity.health > entity.maxHealth) entity.health = entity.maxHealth
                iter.remove()
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.invalid) return
        val id = e.player.uniqueID
        var currentMana = manaPool[id] ?: 0f
        val reclvl = getItemSpecificEnchantLevel(e.player.heldItemMainhand, EnchantCelestialBlessing)

        if (currentMana > 1f && reclvl > 0 && !e.player.world.isRemote) {
            if (e.player.foodStats.foodLevel < 20) {
                val recovery = 20 - e.player.foodStats.foodLevel.coerceAtMost(currentMana.roundToInt())
                if (recovery <= currentMana) {
                    currentMana -= recovery
                    e.player.addExperience(recovery)
                    e.player.foodStats.foodLevel += recovery
                }
            } else if (e.player.foodStats.saturationLevel < 20) {
                val recovery = 20 - e.player.foodStats.saturationLevel.coerceAtMost(currentMana)
                if (currentMana >= recovery) {
                    currentMana -= recovery
                    e.player.addExperience(recovery.roundToInt())
                    e.player.foodStats.saturation += recovery
                }
            }
            if (e.player.health < e.player.maxHealth) {
                val healingCount = if (e.player.lostHealth > currentMana) currentMana else e.player.lostHealth
                val cost = healingCount
                if (currentMana >= cost) {
                    currentMana -= cost
                    e.player.heal(healingCount)
                }
            }
            e.player.displayedItem.forEach { stack ->
                val cost = (stack.itemDamage).coerceAtMost(currentMana.roundToInt())
                if (stack.isItemDamaged) {
                    currentMana -= cost
                    stack.itemDamage -= cost
                }
            }
        }

        if (!e.player.world.isRemote && reclvl > 0 && currentMana > 0f
            && e.player.ticksExisted % 4 == 0 && GeneralConfig.enabledSpecialParticles) {
            NetworkManager.sendToAllAround(
                PacketCelestialRing(e.player.uniqueID),
                e.player.dimension,
                e.player.posX, e.player.posY, e.player.posZ,
                64.0
            )
        }

        if (reclvl > 0 && currentMana > 0f) {
            val cd = (autoAttackCooldown[id] ?: 0) - 1
            if (cd > 0) {
                autoAttackCooldown[id] = cd
            } else {
                val queue = targetQueue[id]
                val target = findNextTarget(e.player, queue)

                if (target != null) {
                    val lvl = getItemSpecificEnchantLevel(e.player.heldItemMainhand, EnchantCelestialBlessing)
                    val cost = (currentMana * 0.01f * lvl).coerceAtLeast(1f)
                    if (currentMana >= cost) {
                        currentMana -= cost
                        manaPool[id] = currentMana

                        val projDamage = (cost * lvl * 0.16f)
                        projectiles.add(
                            CelestialProjectile(
                                e.player.posX, e.player.posY + 1.2, e.player.posZ,
                                target.uniqueID, id, projDamage, lvl,
                                remainingHits = lvl
                            )
                        )
                        autoAttackCooldown[id] = 30 - lvl * 5
                    }
                } else {
                    queue?.clear()
                }
            }
        }
        if (currentMana > 0f) {
            val cost = (currentMana * 0.001f).coerceIn(0.05f, 50f)
            e.player.addExperience((cost).roundToInt())
            currentMana -= cost
        } else {
            manaPool.remove(id)
            if (e.player is EntityPlayerMP) {
                if (lastSyncedMana[id] != 0f) {
                    NetworkManager.sendToClient(
                        PacketManaPoolSync(id, 0f),
                        e.player as EntityPlayerMP
                    )
                    lastSyncedMana[id] = 0f
                }
            }
            return
        }
        if (manaPool.containsKey(id)) {
            manaPool[id] = currentMana
        }
        tickProjectiles(e.player.world)

        if (reclvl > 0) {
            val kbAttr = e.player.getEntityAttribute(
                SharedMonsterAttributes.KNOCKBACK_RESISTANCE
            )
            val speedAttr = e.player.getEntityAttribute(
                SharedMonsterAttributes.ATTACK_SPEED
            )
            if (kbAttr != null) {
                val hasKBModifier = kbAttr.getModifier(KNOCKBACK_UUID) != null
                if (currentMana > 0f && !hasKBModifier) {
                    kbAttr.applyModifier(
                        AttributeModifier(
                            KNOCKBACK_UUID,
                            "Celestial Blessing Knockback Resist Booster",
                            1.0,
                            0
                        )
                    )
                } else if (currentMana <= 0f && hasKBModifier) {
                    kbAttr.removeModifier(KNOCKBACK_UUID)
                }
            }
            if (speedAttr != null) {
                val hasATKSpeedModifier = speedAttr.getModifier(ATTACK_SPEED_UUID) != null
                if (currentMana > 0f && !hasATKSpeedModifier) {
                    speedAttr.applyModifier(
                        AttributeModifier(
                            ATTACK_SPEED_UUID,
                            "Celestial Blessing Attack Speed Booster",
                            0.750 * reclvl,
                            2
                        )
                    )
                } else if (currentMana <= 0f && hasATKSpeedModifier) {
                    speedAttr.removeModifier(ATTACK_SPEED_UUID)
                }
            }
            e.player.syncAttributes()
        }
        val lastSync = lastSyncedMana[id]
        val lastTick = lastSyncTick[id] ?: -100
        val changed = lastSync == null || abs(lastSync - currentMana) > 0.01f
        val cooldownPassed = e.player.ticksExisted - lastTick >= 1

        if (changed && cooldownPassed) {
            NetworkManager.sendToClient(
                PacketManaPoolSync(id, currentMana),
                e.player as EntityPlayerMP
            )
            lastSyncedMana[id] = currentMana
            lastSyncTick[id] = e.player.ticksExisted
        }
    }

    private fun findNextTarget(player: EntityPlayer, queue: MutableList<UUID>?): EntityLivingBase? {
        if (queue == null || queue.isEmpty()) return null

        val iterator = queue.iterator()
        while (iterator.hasNext()) {
            val uuid = iterator.next()
            val entity = player.world.getEntities(EntityLivingBase::class.java) {
                it != null && it.uniqueID == uuid
            }.firstOrNull()

            if (entity == null) { iterator.remove(); continue }
            if (!entity.isEntityAlive) {
                if (entity.deathTime <= 1) return entity
                iterator.remove(); continue
            }
            if (entity.isFriendlyTo(player)) { iterator.remove(); continue }
            if (entity.getDistance(player) > 64.0) continue
            if (!player.world.canEntitySee(player, entity)) continue
            return entity
        }
        return null
    }

    private fun tickProjectiles(world: World) {
        val iterator = projectiles.iterator()
        while (iterator.hasNext()) {
            val proj = iterator.next()
            proj.life++
            if (proj.hitCooldown > 0) proj.hitCooldown--

            if (proj.life > 200) {
                iterator.remove()
                continue
            }

            val target = world.getEntities(EntityLivingBase::class.java) {
                it?.uniqueID == proj.targetUUID
            }.firstOrNull()

            if (target == null || target.isDead) {
                iterator.remove()
                continue
            }

            val tx = target.posX
            val ty = target.posY + target.height / 2.0
            val tz = target.posZ
            val dx = tx - proj.x
            val dy = ty - proj.y
            val dz = tz - proj.z
            val dist = sqrt(dx * dx + dy * dy + dz * dz)

            if (dist <= 2.0) {
                if (proj.hitCooldown <= 0) {
                    val owner = world.getPlayerEntityByUUID(proj.ownerUUID)
                    if (owner != null && target.isEntityAlive) {
                        target.runPlayerAttack(owner, proj.damage, true, CelestialDamageSources.dealDamage(owner))
                    }

                    if (world is WorldServer) {
                        world.spawnParticle(EnumParticleTypes.END_ROD, tx, ty, tz, 5, 0.3, 0.3, 0.3, 0.05)
                        world.playSound(null, tx, ty, tz, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.5f, 1.5f)
                    }

                    proj.remainingHits--
                    proj.hitCooldown = 1

                    if (proj.remainingHits <= 0) {
                        iterator.remove()
                        continue
                    }

                    val owner2 = world.getPlayerEntityByUUID(proj.ownerUUID)
                    if (owner2 == null) {
                        iterator.remove()
                        continue
                    }

                    val currentStillValid = target.isEntityAlive
                            && target.getDistance(proj.x, proj.y, proj.z) <= 64.0

                    if (currentStillValid) {
                        proj.targetUUID = target.uniqueID
                    } else {
                        val nextTarget = findChainTarget(world, owner2, proj)
                        if (nextTarget == null) {
                            iterator.remove()
                            continue
                        }
                        proj.targetUUID = nextTarget.uniqueID
                    }
                }

                if (dist > proj.level * 0.4) {
                    val speed = proj.level * 0.8
                    proj.x += dx / dist * speed
                    proj.y += dy / dist * speed
                    proj.z += dz / dist * speed
                }
            } else {
                val speed = proj.level * 1.2
                proj.x += dx / dist * speed
                proj.y += dy / dist * speed
                proj.z += dz / dist * speed

                if (world is WorldServer) {
                    world.spawnParticle(EnumParticleTypes.END_ROD, proj.x, proj.y, proj.z, 1, 0.0, 0.0, 0.0, 0.0)
                }
            }
        }
    }

    private fun findChainTarget(world: World, owner: EntityPlayer, proj: CelestialProjectile): EntityLivingBase? {
        val candidates = world.getEntities(EntityLivingBase::class.java) { candidate ->
            candidate != null
                    && candidate.isEntityAlive
                    && candidate.isValidChainTarget(owner)
                    && candidate.getDistance(proj.x, proj.y, proj.z) <= 64.0
                    && world.canPositionSee(proj.x, proj.y, proj.z, candidate)
        }
        return candidates.minByOrNull { it.getDistance(proj.x, proj.y, proj.z) }
    }

    private fun clearAttributeModifiers(p: EntityPlayer) {
        p.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE)
            .removeModifier(KNOCKBACK_UUID)
        p.getEntityAttribute(SharedMonsterAttributes.ATTACK_SPEED)
            .removeModifier(ATTACK_SPEED_UUID)
    }

    @SubscribeEvent
    fun onPlayerClone(event: net.minecraftforge.event.entity.player.PlayerEvent.Clone) {
        if (!event.isWasDeath) return
        val id = event.original.uniqueID
        val newPlayer = event.entityPlayer

        manaPool.remove(id)
        targetQueue.remove(id)
        autoAttackCooldown.remove(id)
        lastSyncedMana[id] = 0f
        lastSyncTick.remove(id)
        clearAttributeModifiers(newPlayer)
        if (newPlayer is EntityPlayerMP) {
            NetworkManager.sendToClient(
                PacketManaPoolSync(id, 0f),
                newPlayer
            )
        }
    }

    @SubscribeEvent
    fun onLogout(e: PlayerEvent.PlayerLoggedOutEvent) {
        val id = e.player.uniqueID
        manaPool.remove(id)
        targetQueue.remove(id)
        autoAttackCooldown.remove(id)
        lastSyncedMana.remove(id)
        lastSyncTick.remove(id)
        clearAttributeModifiers(e.player)
    }

    @SubscribeEvent
    fun onClientDisconnect(e: net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientDisconnectionFromServerEvent) {
        ClientManaPoolCache.clear()
    }
}