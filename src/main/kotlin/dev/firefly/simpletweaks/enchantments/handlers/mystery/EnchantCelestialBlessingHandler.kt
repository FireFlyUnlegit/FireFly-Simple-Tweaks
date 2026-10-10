package dev.firefly.simpletweaks.enchantments.handlers.mystery

import dev.firefly.simpletweaks.SimpleTweaks
import dev.firefly.simpletweaks.compat.event.AttackEntityEvent
import dev.firefly.simpletweaks.compat.event.EventPriority
import dev.firefly.simpletweaks.compat.event.LivingDamageEvent
import dev.firefly.simpletweaks.compat.event.PlayerEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.event.TickEvent
import dev.firefly.simpletweaks.compat.attacker
import dev.firefly.simpletweaks.compat.cancel
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.core.config.GeneralConfig
import dev.firefly.simpletweaks.network.NetworkManager
import dev.firefly.simpletweaks.network.packets.PacketCelestialRing
import dev.firefly.simpletweaks.network.packets.PacketManaPoolSync
import dev.firefly.simpletweaks.util.canEntitySee
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.hasLineOfSight
import dev.firefly.simpletweaks.util.runPlayerAttack
import dev.firefly.simpletweaks.util.syncAttributes
import dev.firefly.simpletweaks.util.damagesource.CelestialDamageSources
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.passive.TameableEntity
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributeInstance
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.mob.MobEntity
import net.minecraft.entity.mob.Monster
import net.minecraft.entity.passive.AnimalEntity
import net.minecraft.entity.passive.VillagerEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.util.Identifier
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import java.lang.ref.WeakReference
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment

/**
 * `celestial_blessing` — the mod's largest single handler. Port of 1.12.2
 * `enchantments/handlers/mystery/EnchantCelestialBlessingHandler.kt` (534 lines).
 *
 * <h2>What it does</h2>
 * A pooled "mana" resource, fed by the wielder's own damage dealt and spent on:
 * self-healing, hunger/saturation refill, item repair, knockback immunity, attack-speed boost,
 * a max-health "shred" debuff on whatever it hits, damage absorption while the pool lasts, and an
 * orbiting aura with auto-firing chain projectiles.
 *
 * <h2>Mapping decisions that needed a call rather than a lookup</h2>
 * <table>
 *   <tr><th>1.12.2</th><th>here</th><th>why</th></tr>
 *   <tr><td>`isCreatureType(EnumCreatureType.MONSTER, false)`</td><td>`this is Monster`</td>
 *       <td>1.21 removed `EnumCreatureType` and ships no monster entity-type tag (verified: 35 tags,
 *           none is a monster tag). `Monster` is the 1.21 idiom and the author's chosen mapping; it is
 *           a **near** match, not an identical set.</td></tr>
 *   <tr><td>`EntityLiving.attackTarget != null`</td><td>`MobEntity.target != null`</td><td>direct rename</td></tr>
 *   <tr><td>`EntityCreature` / `EntityAnimal` / `EntityVillager`</td>
 *       <td>`MobEntity` / `AnimalEntity` / `VillagerEntity`</td>
 *       <td>`MobEntity` is the base of the same family. Order is preserved (animal and villager are
 *           tested before the generic creature case), so the classification is unchanged.</td></tr>
 *   <tr><td>`UUID.nameUUIDFromBytes(...)` for the three modifiers</td><td>`Identifier.of(MOD_ID, …)`</td>
 *       <td>1.21 `EntityAttributeModifier` is keyed by `Identifier`; same pattern the earlier batches used.</td></tr>
 *   <tr><td>`applyModifier(newModifier)` with `setSaved(false)`</td><td>`addTemporaryModifier(...)`</td>
 *       <td>1.21 split "saved across reload" from "transient"; `setSaved(false)` is the transient case.</td></tr>
 *   <tr><td>`world.getEntities(Class) { uuid match }`</td><td>`ServerWorld#getEntity(UUID)`</td>
 *       <td>1.21 has a direct UUID lookup, so the linear scan over every loaded entity is gone.</td></tr>
 *   <tr><td>`world.canPositionSee(x, y, z, entity)`</td><td>`World#hasLineOfSight(Vec3d, Vec3d)`</td>
 *       <td>the entity overload the port already has; the point overload was never ported.</td></tr>
 * </table>
 *
 * <h2>One 1.12.2 method deliberately **not** ported</h2>
 * `onClientDisconnect` cleared the client-side mana cache from Forge's
 * `ClientDisconnectionFromServerEvent`. That would have forced this **common** class to import
 * `client/ClientManaPoolCache`, which must not be loaded on a dedicated server. The clearing is
 * instead done in [dev.firefly.simpletweaks.SimpleTweaksClient] via
 * `ClientPlayConnectionEvents.DISCONNECT`, where it belongs in 1.21.
 */
@ModEnchantment(
    id = "celestial_blessing",
    category = EnchantCategory.MYSTERY,
    type = EnchantType.SWORD,
    maxLevel = 5,
    weight = 1,
    anvilCost = 14,
    minCostBase = 30,
    minCostPerLevel = 30,
    supportedItems = "#minecraft:enchantable/sword",
    slots = [EnchantSlot.MAINHAND],
    damagePerLevel = 2.0,
    order = 50,
    jsonEmit = false,
)
object EnchantCelestialBlessingHandler : Listenable {

    val manaPool = mutableMapOf<UUID, Float>()

    private val targetQueue = mutableMapOf<UUID, MutableList<UUID>>()

    private val autoAttackCooldown = mutableMapOf<UUID, Int>()
    private val projectiles = mutableListOf<CelestialProjectile>()

    private val KNOCKBACK_ID: Identifier = Identifier.of(SimpleTweaks.MOD_ID, "celestial_knockback")
    private val ATTACK_SPEED_ID: Identifier = Identifier.of(SimpleTweaks.MOD_ID, "celestial_attack_speed")
    private val SHRED_ID: Identifier = Identifier.of(SimpleTweaks.MOD_ID, "celestial_shred")

    private val lastSyncedMana = mutableMapOf<UUID, Float>()
    private val lastSyncTick = mutableMapOf<UUID, Int>()

    /** 60 秒 = 1200 tick */
    private const val SHRED_DURATION_TICKS = 1200

    /** 记录被削减最大生命值的目标及其过期 tick */
    private class ShredState(val ref: WeakReference<LivingEntity>) {
        var expiryTick: Long = 0
    }

    private val shredStates = mutableMapOf<UUID, ShredState>()

    private fun LivingEntity.isFriendlyTo(player: PlayerEntity): Boolean {
        if (this === player) return true
        if (this is PlayerEntity) return true
        if (this is TameableEntity && this.isTamed() && this.owner === player) return true
        if (this.vehicle === player) return true
        return false
    }

    private fun LivingEntity.isHostile(): Boolean {
        if (this is Monster) return true
        if (this is MobEntity && this.target != null) return true
        return false
    }

    private fun LivingEntity.isNeutral(player: PlayerEntity): Boolean {
        if (this is PlayerEntity) return false
        if (this.isFriendlyTo(player)) return false
        if (this.isHostile()) return false
        if (this is AnimalEntity) return false
        if (this is VillagerEntity) return false
        if (this is MobEntity) return true
        return false
    }

    private fun LivingEntity.isValidChainTarget(player: PlayerEntity): Boolean =
        this.isAlive && (this.isHostile() || this.isNeutral(player))

    private data class CelestialProjectile(
        var x: Double, var y: Double, var z: Double,
        var targetUuid: UUID,
        val ownerUuid: UUID,
        val damage: Float,
        val level: Int = 0,
        var life: Int = 0,
        var remainingHits: Int = 0,
        var hitCooldown: Int = 0,
    )

    @SubscribeEvent
    fun onPlayerAttack(event: AttackEntityEvent) {
        val player = event.player
        val target = event.target
        if (target is LivingEntity) {
            val atklvl = getItemSpecificEnchantLevel(player.mainHandStack, GeneratedEnchantments.CELESTIAL_BLESSING)
            if (atklvl > 0) {
                val queue = targetQueue.getOrPut(player.uuid) { mutableListOf() }
                if (!queue.contains(target.uuid)) {
                    queue.add(target.uuid)
                    if (queue.size > 32) queue.removeAt(0)
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onEntityDamage(e: LivingDamageEvent) {
        if (e.invalid) return
        val p = e.attacker ?: return
        val t = e.entityLiving
        val pid = p.uuid
        val tid = t.uuid
        val atklvl = getItemSpecificEnchantLevel(p.mainHandStack, GeneratedEnchantments.CELESTIAL_BLESSING)
        val reclvl = getItemSpecificEnchantLevel(t.mainHandStack, GeneratedEnchantments.CELESTIAL_BLESSING)

        if (atklvl > 0) {
            val healing = (e.amount * 0.18f * atklvl)
            val manaBoost = (e.amount * 0.025f * atklvl)
            val cost = ((manaPool[pid] ?: 0f) * 0.02f * atklvl).coerceAtMost(t.maxHealth * 2f)
            val extraDmg = healing + cost
            manaPool[pid] = (manaPool[pid] ?: 0f) + (healing + manaBoost - cost).coerceIn(0f, t.maxHealth * 2f)
            p.heal(healing)
            e.amount += extraDmg
            val reduction = (e.amount) * 0.04f * atklvl + 1f
            t.timeUntilRegen = 0

            val maxHealthAttr = t.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH)
            if (maxHealthAttr != null) {
                val now = t.world.time
                val state = shredStates[tid]
                if (state != null && now > state.expiryTick) {
                    maxHealthAttr.removeModifier(SHRED_ID)
                    shredStates.remove(tid)
                }

                val existingModifier = maxHealthAttr.getModifier(SHRED_ID)
                val currentReduction = existingModifier?.value ?: 0.0
                val newReduction = currentReduction - reduction
                val maxPossibleReduction = -(maxHealthAttr.baseValue - 1.0)
                val safeReduction = newReduction.coerceAtLeast(maxPossibleReduction)

                if (existingModifier != null) maxHealthAttr.removeModifier(existingModifier)
                maxHealthAttr.addTemporaryModifier(
                    EntityAttributeModifier(SHRED_ID, safeReduction, EntityAttributeModifier.Operation.ADD_VALUE)
                )

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
                t.timeUntilRegen += reclvl * 4
                (t as? PlayerEntity)?.addExperience(incomingDamage.roundToInt())
                manaPool[tid] = currentMana - incomingDamage
                if (t is PlayerEntity) {
                    t.world.playSound(
                        null, t.x, t.y, t.z,
                        SoundEvents.BLOCK_ANVIL_USE, SoundCategory.PLAYERS, 1f, 1.2f,
                    )
                }
            } else if (currentMana > 0f) {
                val overflowDamage = (incomingDamage - currentMana) / defenseFactor
                (t as? PlayerEntity)?.addExperience(currentMana.roundToInt())
                e.amount = overflowDamage
                t.timeUntilRegen += reclvl * 4
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
            if (entity == null || entity.isRemoved || !entity.isAlive) {
                iter.remove()
                continue
            }
            val now = entity.world.time
            if (now > state.expiryTick) {
                entity.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH)?.removeModifier(SHRED_ID)
                if (entity.health > entity.maxHealth) entity.health = entity.maxHealth
                iter.remove()
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.invalid) return
        val player = e.player
        val id = player.uuid
        var currentMana = manaPool[id] ?: 0f
        val reclvl = getItemSpecificEnchantLevel(player.mainHandStack, GeneratedEnchantments.CELESTIAL_BLESSING)

        if (currentMana > 1f && reclvl > 0 && !player.world.isClient) {
            val hunger = player.hungerManager
            if (hunger.foodLevel < 20) {
                val recovery = 20 - hunger.foodLevel.coerceAtMost(currentMana.roundToInt())
                if (recovery <= currentMana) {
                    currentMana -= recovery
                    player.addExperience(recovery)
                    hunger.foodLevel += recovery
                }
            } else if (hunger.saturationLevel < 20) {
                val recovery = 20 - hunger.saturationLevel.coerceAtMost(currentMana)
                if (currentMana >= recovery) {
                    currentMana -= recovery
                    player.addExperience(recovery.roundToInt())
                    hunger.saturationLevel += recovery
                }
            }
            if (player.health < player.maxHealth) {
                val lost = player.maxHealth - player.health
                val healingCount = if (lost > currentMana) currentMana else lost
                if (currentMana >= healingCount) {
                    currentMana -= healingCount
                    player.heal(healingCount)
                }
            }
            // 1.12.2's `displayedItem` is the held item(s) that are actually rendered.
            for (stack in listOf(player.mainHandStack, player.offHandStack)) {
                if (!stack.isDamaged) continue
                val cost = stack.damage.coerceAtMost(currentMana.roundToInt())
                currentMana -= cost
                stack.damage -= cost
            }
        }

        val serverPlayer = player as? ServerPlayerEntity

        if (!player.world.isClient && reclvl > 0 && currentMana > 0f &&
            player.age % 4 == 0 && GeneralConfig.enabledSpecialParticles
        ) {
            val world = player.world as? ServerWorld
            if (world != null) {
                NetworkManager.sendToAllAround(
                    PacketCelestialRing(player.uuid),
                    world,
                    player.x, player.y, player.z,
                    64.0,
                )
            }
        }

        if (reclvl > 0 && currentMana > 0f) {
            val cd = (autoAttackCooldown[id] ?: 0) - 1
            if (cd > 0) {
                autoAttackCooldown[id] = cd
            } else {
                val queue = targetQueue[id]
                val target = findNextTarget(player, queue)

                if (target != null) {
                    val lvl = getItemSpecificEnchantLevel(player.mainHandStack, GeneratedEnchantments.CELESTIAL_BLESSING)
                    val cost = (currentMana * 0.01f * lvl).coerceAtLeast(1f)
                    if (currentMana >= cost) {
                        currentMana -= cost
                        manaPool[id] = currentMana

                        val projDamage = (cost * lvl * 0.16f)
                        projectiles.add(
                            CelestialProjectile(
                                player.x, player.y + 1.2, player.z,
                                target.uuid, id, projDamage, lvl,
                                remainingHits = lvl,
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
            player.addExperience((cost).roundToInt())
            currentMana -= cost
        } else {
            manaPool.remove(id)
            if (serverPlayer != null) {
                if (lastSyncedMana[id] != 0f) {
                    NetworkManager.sendToClient(PacketManaPoolSync(id, 0f), serverPlayer)
                    lastSyncedMana[id] = 0f
                }
            }
            return
        }
        if (manaPool.containsKey(id)) {
            manaPool[id] = currentMana
        }
        (player.world as? ServerWorld)?.let { tickProjectiles(it) }

        if (reclvl > 0) {
            val kbAttr = player.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE)
            val speedAttr = player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_SPEED)
            if (kbAttr != null) {
                val hasKbModifier = kbAttr.getModifier(KNOCKBACK_ID) != null
                if (currentMana > 0f && !hasKbModifier) {
                    kbAttr.addTemporaryModifier(
                        EntityAttributeModifier(KNOCKBACK_ID, 1.0, EntityAttributeModifier.Operation.ADD_VALUE)
                    )
                } else if (currentMana <= 0f && hasKbModifier) {
                    kbAttr.removeModifier(KNOCKBACK_ID)
                }
            }
            if (speedAttr != null) {
                val hasAtkSpeedModifier = speedAttr.getModifier(ATTACK_SPEED_ID) != null
                if (currentMana > 0f && !hasAtkSpeedModifier) {
                    speedAttr.addTemporaryModifier(
                        EntityAttributeModifier(
                            ATTACK_SPEED_ID, 0.750 * reclvl,
                            EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                        )
                    )
                } else if (currentMana <= 0f && hasAtkSpeedModifier) {
                    speedAttr.removeModifier(ATTACK_SPEED_ID)
                }
            }
            player.syncAttributes()
        }

        if (serverPlayer == null) return

        val lastSync = lastSyncedMana[id]
        val lastTick = lastSyncTick[id] ?: -100
        val changed = lastSync == null || abs(lastSync - currentMana) > 0.01f
        val cooldownPassed = player.age - lastTick >= 1

        if (changed && cooldownPassed) {
            NetworkManager.sendToClient(PacketManaPoolSync(id, currentMana), serverPlayer)
            lastSyncedMana[id] = currentMana
            lastSyncTick[id] = player.age
        }
    }

    private fun findNextTarget(player: PlayerEntity, queue: MutableList<UUID>?): LivingEntity? {
        if (queue == null || queue.isEmpty()) return null
        val world = player.world as? ServerWorld ?: return null

        val iterator = queue.iterator()
        while (iterator.hasNext()) {
            val uuid = iterator.next()
            val entity = world.getEntity(uuid) as? LivingEntity
            if (entity == null) {
                iterator.remove(); continue
            }
            if (!entity.isAlive) {
                if (entity.deathTime <= 1) return entity
                iterator.remove(); continue
            }
            if (entity.isFriendlyTo(player)) {
                iterator.remove(); continue
            }
            if (entity.distanceTo(player) > 64.0) continue
            if (!player.world.canEntitySee(player, entity)) continue
            return entity
        }
        return null
    }

    private fun tickProjectiles(world: ServerWorld) {
        val iterator = projectiles.iterator()
        while (iterator.hasNext()) {
            val proj = iterator.next()
            proj.life++
            if (proj.hitCooldown > 0) proj.hitCooldown--

            if (proj.life > 200) {
                iterator.remove(); continue
            }

            val target = world.getEntity(proj.targetUuid) as? LivingEntity
            if (target == null || target.isRemoved) {
                iterator.remove(); continue
            }

            val tx = target.x
            val ty = target.y + target.height / 2.0
            val tz = target.z
            val dx = tx - proj.x
            val dy = ty - proj.y
            val dz = tz - proj.z
            val dist = sqrt(dx * dx + dy * dy + dz * dz)

            if (dist <= 2.0) {
                if (proj.hitCooldown <= 0) {
                    val owner = world.getPlayerByUuid(proj.ownerUuid)
                    if (owner != null && target.isAlive) {
                        target.runPlayerAttack(
                            owner, proj.damage, true, CelestialDamageSources.dealDamage(owner),
                        )
                    }

                    world.spawnParticles(ParticleTypes.END_ROD, tx, ty, tz, 5, 0.3, 0.3, 0.3, 0.05)
                    world.playSound(
                        null, tx, ty, tz,
                        SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.5f, 1.5f,
                    )

                    proj.remainingHits--
                    proj.hitCooldown = 1

                    if (proj.remainingHits <= 0) {
                        iterator.remove(); continue
                    }

                    val owner2 = world.getPlayerByUuid(proj.ownerUuid)
                    if (owner2 == null) {
                        iterator.remove(); continue
                    }

                    val currentStillValid = target.isAlive &&
                        sqrt(target.squaredDistanceTo(proj.x, proj.y, proj.z)) <= 64.0

                    if (currentStillValid) {
                        proj.targetUuid = target.uuid
                    } else {
                        val nextTarget = findChainTarget(world, owner2, proj)
                        if (nextTarget == null) {
                            iterator.remove(); continue
                        }
                        proj.targetUuid = nextTarget.uuid
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

                world.spawnParticles(ParticleTypes.END_ROD, proj.x, proj.y, proj.z, 1, 0.0, 0.0, 0.0, 0.0)
            }
        }
    }

    private fun findChainTarget(world: ServerWorld, owner: PlayerEntity, proj: CelestialProjectile): LivingEntity? {
        val box = Box(
            proj.x - 64.0, proj.y - 64.0, proj.z - 64.0,
            proj.x + 64.0, proj.y + 64.0, proj.z + 64.0,
        )
        // 1.12.2 scanned every loaded entity and filtered by distance / line of sight; the box is the
        // same predicate bounded first, which is what 1.21's query API asks for.
        val candidates = world.getEntitiesByClass(LivingEntity::class.java, box) { candidate ->
            candidate.isAlive &&
                candidate.isValidChainTarget(owner) &&
                sqrt(candidate.squaredDistanceTo(proj.x, proj.y, proj.z)) <= 64.0 &&
                world.hasLineOfSight(
                    Vec3d(proj.x, proj.y, proj.z),
                    Vec3d(candidate.x, candidate.y + candidate.height / 2.0, candidate.z),
                    ignorePassable = false,
                )
        }
        return candidates.minByOrNull { it.squaredDistanceTo(proj.x, proj.y, proj.z) }
    }

    private fun clearEntityAttributeModifiers(p: PlayerEntity) {
        p.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE)?.removeModifier(KNOCKBACK_ID)
        p.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_SPEED)?.removeModifier(ATTACK_SPEED_ID)
    }

    @SubscribeEvent
    fun onPlayerClone(event: PlayerEvent.Clone) {
        if (!event.wasDeath) return
        val id = event.original.uuid
        val newPlayer = event.player

        manaPool.remove(id)
        targetQueue.remove(id)
        autoAttackCooldown.remove(id)
        lastSyncedMana[id] = 0f
        lastSyncTick.remove(id)
        clearEntityAttributeModifiers(newPlayer)
        (newPlayer as? ServerPlayerEntity)?.let {
            NetworkManager.sendToClient(PacketManaPoolSync(id, 0f), it)
        }
    }

    /**
     * 1.12.2 also had `onClientDisconnect` here, clearing the client mana cache. It is **not** ported:
     * this is a common class, and touching `client/ClientManaPoolCache` would pull a client-only class
     * onto a dedicated server. `SimpleTweaksClient` performs the same clear on
     * `ClientPlayConnectionEvents.DISCONNECT`.
     */
    @SubscribeEvent
    fun onLogout(e: PlayerEvent.PlayerLoggedOutEvent) {
        val id = e.player.uuid
        manaPool.remove(id)
        targetQueue.remove(id)
        autoAttackCooldown.remove(id)
        lastSyncedMana.remove(id)
        lastSyncTick.remove(id)
        clearEntityAttributeModifiers(e.player)
    }
}
