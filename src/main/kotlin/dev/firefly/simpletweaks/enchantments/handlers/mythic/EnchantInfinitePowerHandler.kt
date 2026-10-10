package dev.firefly.simpletweaks.enchantments.handlers.mythic

import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.boss.dragon.EnderDragonEntity
import net.minecraft.entity.boss.dragon.phase.PhaseType
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments

/**
 * `infinite_power` core: the one-shot kill, plus the laser body.
 *
 * Port of 1.12.2 `enchantments/handlers/mythic/EnchantInfinitePowerHandler.kt` (367 lines) — the part
 * that **does not** depend on a GUI or a container. The bag side
 * (`InfiniteBagInventory` / `ContainerInfiniteBag` / `GuiInfiniteBag` / `InfiniteContainerHandler` /
 * `SaveHandler`) and the laser *trigger* are still missing; see §"Not ported yet" below.
 *
 * <h2>⚠️ The kill deliberately bypasses the damage pipeline</h2>
 * 1.12.2 did:
 * <pre>
 *   target.health = 0f
 *   target.isDead = true
 *   target.onDeath(DamageSource.causePlayerDamage(player))
 * </pre>
 * — no `damage()` call, so armour, Resistance, enchantment protection and mob damage immunity never
 * see it. That is the whole point: the author's original report was "the warden cannot be killed with
 * this sword", and the warden's damage reduction lives in the pipeline. Writing this as
 * "deal `Int.MAX_VALUE` damage" would be the classic name-matches-semantics-doesn't port: it *looks*
 * equivalent and gets eaten by exactly the mitigation the feature exists to bypass.
 *
 * <h2>Yarn 1.21.1 mapping</h2>
 * | 1.12.2 | 1.21.1 |
 * |---|---|
 * | `entityLiving.health = 0f` | `LivingEntity#setHealth(0f)` |
 * | `entityLiving.isDead = true` | dropped — `onDeath` sets the internal `dead` flag itself |
 * | `entityLiving.onDeath(source)` | `LivingEntity#onDeath(DamageSource)` (unchanged name) |
 * | `DamageSource.causePlayerDamage(player)` | `player.damageSources.playerAttack(player)` |
 * | `world.getEntitiesWithinAABB(Class, AxisAlignedBB)` | `world.getEntitiesByClass(Class, Box) { true }` |
 * | `EntityItem` / `EntityPlayer` | `ItemEntity` / `PlayerEntity` |
 * | `EnumParticleTypes.*` | `ParticleTypes.*` (`ENCHANTMENT_TABLE`→`ENCHANT`, `FIREWORKS_SPARK`→`FIREWORK`, `SPELL_MOB`→`EFFECT`) |
 * | `world.removeEntityDangerously(e)` | `e.remove(Entity.RemovalReason.KILLED)` |
 *
 * <h2>Not ported yet, and why (each needs a seam that does not exist)</h2>
 * | missing piece | blocking seam |
 * |---|---|
 * | laser **trigger** (`ClientHandler`, left-click with an empty hand) | `PlayerInteractEvent.LeftClickEmpty` is declared "not yet provided" in `compat/event/PlayerInteractEvent.kt`; it needs a client input hook |
 * | laser **packet** (`PacketLaser`) | depends on the trigger above |
 * | bag GUI + container | needs a registered `ScreenHandlerType`, an `ExtendedScreenHandlerFactory`, and player-persistent storage |
 * | `LivingDropsEvent` (the `InfiniteContainerHandler`/`DropHandler` "drops go into the bag" halves) | `compat/EventUtils.kt` lists `LivingDropsEvent` as not ported |
 *
 * <p>[fireLaser] is nevertheless ported in full, because it is a pure server-side function whose only
 * missing input is the direction vec — so whichever seam lands first can wire it up without touching
 * this file.
 */
object EnchantInfinitePowerHandler {

    const val AOE_RANGE = 2.0
    const val DROP_COLLECT_RANGE = 2.0
    const val LASER_MAX_DISTANCE = 128.0
    const val LASER_STEP = 1.0
    const val ENTITY_CHECK_RADIUS = 1.25

    /**
     * The committed kill entry point, called from `PlayerEntityAttackMixin` at the same place 1.12.2's
     * `MixinEntityPlayerAttack` sat.
     *
     * <p><b>Deliberately does NOT cancel the attack.</b> 1.12.2 cancelled at HEAD, which in 1.21 would
     * skip both the attack-cooldown reset and `PlayerEntityAttackMixin`'s own
     * `@Inject(at = RETURN)` that clears the crit seam — leaving `EventSeams.critActive()` permanently
     * true and poisoning every later swing. Keeping the vanilla path lets the dead target absorb one
     * harmless no-op `damage()` call. See `docs/infinite-power-deferred.md` §5.1/§5.2/§5.4.
     */
    @JvmStatic
    fun handleAttack(player: PlayerEntity, target: LivingEntity, world: World) {
        if (world.isClient) return

        val pos = target.pos
        if (target is EnderDragonEntity) {
            killDragon(target, world, player)
            return
        }

        kill(target, player)

        // 1.12.2 collected the victim's drops into the killer's inventory within 2 blocks.
        val dropBox = Box(
            pos.x - DROP_COLLECT_RANGE, pos.y - DROP_COLLECT_RANGE, pos.z - DROP_COLLECT_RANGE,
            pos.x + DROP_COLLECT_RANGE, pos.y + DROP_COLLECT_RANGE, pos.z + DROP_COLLECT_RANGE,
        )
        for (item in world.getEntitiesByClass(net.minecraft.entity.ItemEntity::class.java, dropBox) { true }) {
            if (!item.isRemoved && player.inventory.insertStack(item.stack)) {
                item.discard()
            }
        }

        val aoeBox = Box(
            pos.x - AOE_RANGE, pos.y - 1.0, pos.z - AOE_RANGE,
            pos.x + AOE_RANGE, pos.y + 2.0, pos.z + AOE_RANGE,
        )
        for (entity in world.getEntitiesByClass(LivingEntity::class.java, aoeBox) { true }) {
            if (entity === player || entity === target || !entity.isAlive) continue
            if (entity is EnderDragonEntity) {
                killDragon(entity, world, player)
            } else {
                kill(entity, player)
            }
        }

        if (world is ServerWorld) {
            spawnDeathParticles(world, pos)
        }
        world.playSound(
            null,
            target.x, target.y, target.z,
            SoundEvents.ENTITY_GENERIC_EXPLODE,
            SoundCategory.PLAYERS,
            1.0f, 0.8f,
        )
        STLog.log("InfinitePower") {
            "player=${player.name.string}, target=${target.name.string}, outcome=one-shot"
        }
    }

    /** The whole kill, in one place, so the AOE and laser paths cannot drift from the main one. */
    private fun kill(target: LivingEntity, player: PlayerEntity) {
        target.setHealth(0f)
        target.onDeath(player.damageSources.playerAttack(player))
    }

    /**
     * Laser body. Server-side only; `direction` comes from the shooter's look vector.
     *
     * The trigger (`ClientHandler`'s left-click-empty) and its packet are not ported yet, so nothing
     * calls this at present — kept because it is self-contained and the only missing input is the vec.
     */
    @JvmStatic
    fun fireLaser(player: PlayerEntity, direction: Vec3d, world: World) {
        if (world.isClient) return

        val start = player.getEyePos()
        val end = start.add(direction.multiply(LASER_MAX_DISTANCE))
        val hit = world.raycast(net.minecraft.world.RaycastContext(
            start, end,
            net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
            net.minecraft.world.RaycastContext.FluidHandling.NONE,
            player,
        ))
        val actualEnd = hit?.pos ?: end

        val steps = (start.distanceTo(actualEnd) / LASER_STEP).toInt()
        val processed = mutableSetOf<LivingEntity>()
        var anyHit = false

        for (i in 0..steps) {
            val t = i.toDouble() / steps
            val pos = start.add(
                (actualEnd.x - start.x) * t,
                (actualEnd.y - start.y) * t,
                (actualEnd.z - start.z) * t,
            )
            val box = Box(
                pos.x - ENTITY_CHECK_RADIUS, pos.y - ENTITY_CHECK_RADIUS, pos.z - ENTITY_CHECK_RADIUS,
                pos.x + ENTITY_CHECK_RADIUS, pos.y + ENTITY_CHECK_RADIUS, pos.z + ENTITY_CHECK_RADIUS,
            )
            for (entity in world.getEntitiesByClass(LivingEntity::class.java, box) { true }) {
                if (entity === player || !processed.add(entity)) continue
                anyHit = true
                if (entity is EnderDragonEntity) {
                    killDragon(entity, world, player)
                } else {
                    kill(entity, player)
                }
                if (world is ServerWorld) {
                    world.playSound(
                        null, entity.x, entity.y, entity.z,
                        SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1f, 0.8f,
                    )
                    spawnLaserHitParticles(world, entity.pos)
                }
            }
        }

        if (world is ServerWorld) {
            spawnLaserParticles(world, start, actualEnd, anyHit)
        }
    }

    /**
     * The End dragon needs the phase machine, not just zero health — otherwise it dies standing up
     * with no death animation.
     *
     * <p><b>Two deviations from 1.12.2, and they are coupled.</b> The original did four things after
     * zeroing health: `deathTime = 199`, `PhaseList.DYING`, `fightManager.processDragonDeath(dragon)`,
     * and `removeEntityDangerously(dragon)` — i.e. it opened the exit portal <b>by hand</b> and then
     * removed the entity, because removal would otherwise prevent the vanilla sequence from finishing.
     *
     * <p>Here `processDragonDeath` is not called: the field is not public in 1.21, and it does not need
     * to be. `EnderDragonFight#tick` detects the dragon's death itself and runs the portal/egg
     * sequence. <b>That is exactly why the immediate `remove` is gone too</b> — keeping it would skip
     * the body's post-death ticks and leave the End with no exit portal, because nothing else would
     * open one. Health 0 + `phase = DYING` is enough to hand the rest to vanilla.
     */
    private fun killDragon(dragon: EnderDragonEntity, world: World, player: PlayerEntity) {
        if (world.isClient) return
        dragon.setHealth(0f)
        dragon.onDeath(player.damageSources.playerAttack(player))
        dragon.deathTime = 199
        dragon.phaseManager.setPhase(PhaseType.DYING)
        if (world is ServerWorld) {
            spawnDeathParticles(world, dragon.pos)
            world.playSound(
                null, dragon.x, dragon.y, dragon.z,
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.0f, 0.8f,
            )
        }
    }

    // ------------------------------------------------------------------ particles

    /** 1.12.2 `spawnDeathParticles` (200 particles across five rings, in the original order). */
    fun spawnDeathParticles(world: ServerWorld, pos: Vec3d) {
        val rand = Random

        repeat(40) { i ->
            val angle = i * (2 * Math.PI / 40) + rand.nextDouble() * 0.3
            val radius = 0.8 + rand.nextDouble() * 0.6
            world.spawnParticles(
                ParticleTypes.END_ROD,
                pos.x + cos(angle) * radius, pos.y + i * 0.08 + rand.nextDouble() * 0.2,
                pos.z + sin(angle) * radius,
                1, 0.0, 0.0, 0.0, 0.0,
            )
        }
        repeat(40) {
            world.spawnParticles(
                ParticleTypes.DRAGON_BREATH,
                pos.x + (rand.nextDouble() - 0.5) * 3.5, pos.y + rand.nextDouble() * 2.5,
                pos.z + (rand.nextDouble() - 0.5) * 3.5,
                1, 0.0, 0.0, 0.0, 0.0,
            )
        }
        repeat(60) {
            world.spawnParticles(
                ParticleTypes.ENCHANT,
                pos.x + (rand.nextDouble() - 0.5) * 4.5, pos.y + rand.nextDouble() * 3.0 + 0.5,
                pos.z + (rand.nextDouble() - 0.5) * 4.5,
                1, 0.0, 0.0, 0.0, 0.0,
            )
        }
        repeat(50) {
            world.spawnParticles(
                ParticleTypes.FIREWORK,
                pos.x + (rand.nextDouble() - 0.5) * 2.5, pos.y + rand.nextDouble() * 2.0,
                pos.z + (rand.nextDouble() - 0.5) * 2.5,
                1, 0.0, 0.0, 0.0, 0.0,
            )
        }
        repeat(30) {
            val angle = rand.nextDouble() * 2 * Math.PI
            val radius = 1.2 + rand.nextDouble() * 1.2
            world.spawnParticles(
                ParticleTypes.NOTE,
                pos.x + cos(angle) * radius, pos.y + rand.nextDouble() * 1.5 + 0.5,
                pos.z + sin(angle) * radius,
                1, rand.nextDouble(), rand.nextDouble(), rand.nextDouble(), 0.0,
            )
        }
    }

    private fun spawnLaserParticles(world: ServerWorld, start: Vec3d, end: Vec3d, hit: Boolean) {
        val rand = Random
        val steps = (start.distanceTo(end) / LASER_STEP).toInt()

        for (i in 0..steps) {
            val t = i.toDouble() / steps
            val x = start.x + (end.x - start.x) * t
            val y = start.y + (end.y - start.y) * t
            val z = start.z + (end.z - start.z) * t

            world.spawnParticles(ParticleTypes.EFFECT, x, y, z, 1, rand.nextDouble(), rand.nextDouble(), rand.nextDouble(), 0.0)
            if (i % 3 == 0) world.spawnParticles(ParticleTypes.END_ROD, x, y, z, 1, 0.0, 0.0, 0.0, 0.0)
            if (i % 6 == 0) world.spawnParticles(ParticleTypes.FIREWORK, x, y, z, 1, 0.0, 0.0, 0.0, 0.0)
            if (i % 8 == 0) {
                world.spawnParticles(
                    ParticleTypes.ENCHANT,
                    x + (rand.nextDouble() - 0.5) * 0.3, y + (rand.nextDouble() - 0.5) * 0.3 + 0.2,
                    z + (rand.nextDouble() - 0.5) * 0.3,
                    1, rand.nextDouble() * 0.1, rand.nextDouble() * 0.1, rand.nextDouble() * 0.1, 0.0,
                )
            }
        }
        if (hit) spawnLaserHitParticles(world, end)
    }

    /** Unused until the laser trigger lands; kept so the particle work is not lost. */
    @Suppress("unused")
    private fun spawnLaserHitParticles(world: ServerWorld, pos: Vec3d) {
        val rand = Random

        repeat(30) { i ->
            val angle = i * (2 * Math.PI / 30) + rand.nextDouble() * 0.3
            val radius = 0.5 + rand.nextDouble() * 1.2
            world.spawnParticles(
                ParticleTypes.END_ROD,
                pos.x + cos(angle) * radius, pos.y + rand.nextDouble() * 0.5, pos.z + sin(angle) * radius,
                1, 0.0, 0.0, 0.0, 0.0,
            )
        }
        repeat(40) { i ->
            val angle = i * (2 * Math.PI / 40) + rand.nextDouble() * 0.3
            val radius = 0.8 + rand.nextDouble() * 1.5
            world.spawnParticles(
                ParticleTypes.FIREWORK,
                pos.x + cos(angle) * radius, pos.y + rand.nextDouble() * 0.8, pos.z + sin(angle) * radius,
                1, 0.0, 0.0, 0.0, 0.0,
            )
        }
        repeat(30) {
            world.spawnParticles(
                ParticleTypes.ENCHANT,
                pos.x + (rand.nextDouble() - 0.5) * 2.0, pos.y + rand.nextDouble() * 1.5,
                pos.z + (rand.nextDouble() - 0.5) * 2.0,
                1, 0.0, 0.0, 0.0, 0.0,
            )
        }
        repeat(20) {
            val angle = rand.nextDouble() * 2 * Math.PI
            val radius = 0.8 + rand.nextDouble() * 1.0
            world.spawnParticles(
                ParticleTypes.NOTE,
                pos.x + cos(angle) * radius, pos.y + rand.nextDouble() * 0.8, pos.z + sin(angle) * radius,
                1, rand.nextDouble(), rand.nextDouble(), rand.nextDouble(), 0.0,
            )
        }
        repeat(20) {
            world.spawnParticles(
                ParticleTypes.EFFECT,
                pos.x + (rand.nextDouble() - 0.5) * 1.5, pos.y + rand.nextDouble() * 1.0,
                pos.z + (rand.nextDouble() - 0.5) * 1.5,
                1, rand.nextDouble(), rand.nextDouble(), rand.nextDouble(), 0.0,
            )
        }
    }

    /** `DamageSource` is built inside [kill]; kept named so callers read like the original. */
    private fun sourceFor(player: PlayerEntity): DamageSource = player.damageSources.playerAttack(player)

    /** Convenience for the sub-handlers: the level of `infinite_power` on the held stack. */
    @JvmStatic
    fun heldLevel(player: PlayerEntity): Int =
        getItemSpecificEnchantLevel(player.mainHandStack, GeneratedEnchantments.INFINITE_POWER)

    /** Convenience for the sub-handlers: whether the given stack carries `infinite_power`. */
    @JvmStatic
    fun hasPower(stack: ItemStack): Boolean =
        getItemSpecificEnchantLevel(stack, GeneratedEnchantments.INFINITE_POWER) > 0
}
