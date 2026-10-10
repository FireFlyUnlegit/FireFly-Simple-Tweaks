package dev.firefly.simpletweaks.enchantments.handlers.legendary

import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.compat.WorldSide
import dev.firefly.simpletweaks.compat.event.EventPriority
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.event.TickEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.runPlayerAttack
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.projectile.ArrowEntity
import net.minecraft.particle.ParticleTypes
import net.minecraft.registry.RegistryKey
import net.minecraft.server.world.ServerWorld
import net.minecraft.world.World
import java.util.*
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment

/**
 * 1.21 port of `enchantments/handlers/legendary/EnchantEchoShotHandler.kt`.
 *
 * Line-by-line mapping from 1.12.2:
 * | 1.12.2                                                     | 1.21.1                                                                 |
 * |------------------------------------------------------------|------------------------------------------------------------------------|
 * | `net.minecraftforge...LivingHurtEvent` / `...TickEvent`    | `compat.event.LivingHurtEvent` / `compat.event.TickEvent`               |
 * | `net.minecraftforge...EventPriority`                       | `compat.event.EventPriority` (the annotation keeps `priority`)           |
 * | `e.source.immediateSource as? EntityArrow`                 | `e.source.source as? ArrowEntity` — `DamageSource#getSource()` **is** the direct entity that phase-2.5 calls `getDirectEntity()` (verified in the yarn jar: `getSource` = official `getDirectEntity`, `getAttacker` = `getEntity`) |
 * | `arrow.shootingEntity as? EntityPlayer`                    | `arrow.owner as? PlayerEntity` (`ProjectileEntity#getOwner()`)           |
 * | `target.uniqueID` / `shooter.uniqueID`                     | `target.uuid` / `shooter.uuid` (`getUuid()`, declared on `EntityLike`)   |
 * | `target.dimension` / `world.provider.dimension` (ints)     | `target.world.registryKey` / `world.registryKey` (`RegistryKey<World>`)  |
 * | `world.loadedEntityList` scanned for a `uniqueID` match    | `ServerWorld#getEntity(UUID)` — see the comment in `onWorldTick`          |
 * | `world.getPlayerEntityByUUID(uuid)`                        | `world.getPlayerByUuid(uuid)` (`EntityView` default method)              |
 * | `target.posX/posY/posZ`, `target.height`                   | `target.x/y/z`, `target.height`                                          |
 * | `EnumParticleTypes.CRIT_MAGIC`                             | `ParticleTypes.ENCHANTED_HIT` (1.13 renamed `critMagic` -> `enchanted_hit`) |
 * | `EnumParticleTypes.SPELL_WITCH`                            | `ParticleTypes.WITCH` (1.13 renamed `spell_witch` -> `witch`)            |
 * | `world.spawnParticle(type, x,y,z, n, dx,dy,dz, speed)`     | `ServerWorld#spawnParticles(effect, x,y,z, n, dx,dy,dz, speed)` (`method_14199`) |
 * | `EnchantEchoShot` (Enchantment object)                     | `GeneratedEnchantments.ECHO_SHOT` (RegistryKey)                             |
 *
 * ⚠️ Two forced mappings, both reported in the batch report:
 *  1. **`world.loadedEntityList` -> `ServerWorld#getEntity(UUID)`.** 1.21 removed the public
 *     "all loaded entities" list and offers no `World#getEntities()`; the world-scoped
 *     entity-by-UUID lookup is the exact equivalent of the original scan (the "is it a
 *     `LivingEntity`?" filter is kept as a cast, because the original skipped non-living matches).
 *     The query itself is hoisted out of the per-echo loop and the later `if (world is WorldServer)`
 *     particle branch reuses the same cast — behaviour is identical because the handler already
 *     returned for client worlds.
 *  2. **`target.runPlayerAttack(...)`.** The helper exists in `util/PlayerUtils.kt`, but its crit
 *     branch cannot be ported (no `CriticalHitEvent` seam in this batch) — see that function's KDoc.
 *     This call site passes `allowCrit = false`, so the part that could not be mapped was inert here.
 */
@ModEnchantment(
    id = "echo_shot",
    category = EnchantCategory.LEGENDARY,
    type = EnchantType.BOW,
    maxLevel = 5,
    weight = 2,
    anvilCost = 10,
    minCostBase = 37,
    minCostPerLevel = 12,
    supportedItems = "#minecraft:enchantable/bow",
    slots = [EnchantSlot.MAINHAND, EnchantSlot.OFFHAND],
    order = 37,
)
object EnchantEchoShotHandler : Listenable {

    private class DelayedEcho(
        val targetUUID: UUID,
        val ownerUUID: UUID,
        val dimension: RegistryKey<World>,
        val damage: Float,
        var ticksLeft: Int,
    )

    private val echoes = mutableListOf<DelayedEcho>()

    @SubscribeEvent(priority = EventPriority.LOW)
    fun onHurt(e: LivingHurtEvent) {
        val arrow = e.source.source as? ArrowEntity ?: return
        val shooter = arrow.owner as? PlayerEntity ?: return
        val target = e.entityLiving ?: return
        if (target === shooter) return

        val lvl = getItemSpecificEnchantLevel(shooter.mainHandStack, GeneratedEnchantments.ECHO_SHOT)
        if (lvl <= 0) return

        val ratio = 0.35f + 0.07f * lvl
        val delay = (11 - lvl).coerceAtLeast(6)

        echoes.add(
            DelayedEcho(
                targetUUID = target.uuid,
                ownerUUID = shooter.uuid,
                dimension = target.world.registryKey,
                damage = e.amount * ratio,
                ticksLeft = delay,
            )
        )
        STLog.log("EchoShot") {
            "shooter=${shooter.name.string}, target=${target.name.string}, lvl=$lvl, ratio=$ratio, " +
                "hitDamage=${e.amount}, echoDamage=${e.amount * ratio}, delayTicks=$delay, outcome=scheduled"
        }
    }

    @SubscribeEvent
    fun onWorldTick(e: TickEvent.WorldTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        val world = e.world
        if (WorldSide.isClient(world)) return
        if (echoes.isEmpty()) return

        val currentDim = world.registryKey

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

        // 1.12.2 iterated `world.loadedEntityList` looking for the first `EntityLivingBase` whose
        // `uniqueID` matched; 1.21 exposes that same world-scoped lookup directly as
        // `ServerWorld#getEntity(UUID)` (`World` itself has no "loaded entities" accessor any more).
        val serverWorld = world as? ServerWorld ?: return

        for (echo in toFire) {
            val target = serverWorld.getEntity(echo.targetUUID) as? LivingEntity
            if (target == null || !target.isAlive) continue

            val owner = world.getPlayerByUuid(echo.ownerUUID) ?: continue

            target.runPlayerAttack(
                attacker = owner,
                rawDamage = echo.damage,
                forceHit = true,
                triggerEvent = true,
                allowCrit = false,
            )

            serverWorld.spawnParticles(
                ParticleTypes.ENCHANTED_HIT,
                target.x,
                target.y + target.height * 0.6,
                target.z,
                12, 0.3, 0.3, 0.3, 0.1,
            )
            serverWorld.spawnParticles(
                ParticleTypes.WITCH,
                target.x,
                target.y + target.height * 0.6,
                target.z,
                6, 0.3, 0.3, 0.3, 0.05,
            )
            STLog.log("EchoShot") {
                "shooter=${owner.name.string}, target=${target.name.string}, echoDamage=${echo.damage}, " +
                    "outcome=echo-fired"
            }
        }
    }
}
