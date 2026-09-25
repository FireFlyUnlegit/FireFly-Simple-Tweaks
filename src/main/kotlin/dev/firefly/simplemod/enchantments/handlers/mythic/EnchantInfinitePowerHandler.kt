package dev.firefly.simplemod.enchantments.handlers.mythic

import dev.firefly.simplemod.core.Listenable
import dev.firefly.simplemod.core.event.TickEvent
import dev.firefly.simplemod.core.handler
import dev.firefly.simplemod.enchantments.handlers.mythic.infinitepower.*
import dev.firefly.simplemod.enchantments.mythic.EnchantInfinitePower
import dev.firefly.simplemod.network.NetworkManager
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.ai.attributes.AttributeModifier
import net.minecraft.entity.boss.EntityDragon
import net.minecraft.entity.boss.dragon.phase.PhaseList
import net.minecraft.entity.item.EntityItem
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.SoundEvents
import net.minecraft.util.DamageSource
import net.minecraft.util.EnumParticleTypes
import net.minecraft.util.SoundCategory
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import net.minecraft.world.WorldServer
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.Loader
import net.minecraftforge.fml.common.network.IGuiHandler
import net.minecraftforge.fml.common.network.NetworkRegistry
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

object EnchantInfinitePowerHandler : Listenable {

    internal const val AOE_RANGE = 2.0
    internal const val DROP_COLLECT_RANGE = 2.0
    internal const val LASER_MAX_DISTANCE = 128.0
    internal const val LASER_STEP = 1.0
    internal const val ENTITY_CHECK_RADIUS = 1.25

    internal val ATTACK_SPEED_MODIFIER = AttributeModifier(
        UUID.fromString("12345678-1234-1234-1234-123456789abc"),
        "InfiniteSpeed",
        1000.0,
        0
    )

    lateinit var modInstance: Any
        private set

    override fun init() {
        handler<TickEvent> {
            EnchantInfinitePower.tick()
        }

        val container = Loader.instance().indexedModList["simplemod"]
        modInstance = container?.getMod()
            ?: throw RuntimeException("Failed to get mod instance for simplemod")

        NetworkRegistry.INSTANCE.registerGuiHandler(modInstance, object : IGuiHandler {
            override fun getServerGuiElement(id: Int, player: EntityPlayer, world: World, x: Int, y: Int, z: Int): Any {
                return ContainerInfiniteBag(player, InfiniteBagInventory(player))
            }
            override fun getClientGuiElement(id: Int, player: EntityPlayer, world: World, x: Int, y: Int, z: Int): Any {
                return GuiInfiniteBag(player, InfiniteBagInventory(player))
            }
        })

        MinecraftForge.EVENT_BUS.register(ForgeHandler)
        MinecraftForge.EVENT_BUS.register(ClientHandler)
        MinecraftForge.EVENT_BUS.register(FlightHandler)
        MinecraftForge.EVENT_BUS.register(ToolHandler)
        MinecraftForge.EVENT_BUS.register(SoulBindHandler)
        MinecraftForge.EVENT_BUS.register(InfiniteContainerHandler)
        MinecraftForge.EVENT_BUS.register(DropHandler)
        MinecraftForge.EVENT_BUS.register(SaveHandler)
    }

    fun sendLaser(player: EntityPlayer, direction: Vec3d) {
        NetworkManager.sendToServer(PacketLaser(player, direction))
    }

    @JvmStatic
    fun handleAttack(player: EntityPlayer, target: EntityLivingBase, world: World) {
        if (world.isRemote) return
        val pos = target.positionVector
        if (target is EntityDragon) {
            killDragon(target, world, player)
            return
        }
        target.health = 0f
        target.isDead = true
        target.onDeath(DamageSource.causePlayerDamage(player))
        val dropBox = AxisAlignedBB(
            pos.x - DROP_COLLECT_RANGE, pos.y - DROP_COLLECT_RANGE, pos.z - DROP_COLLECT_RANGE,
            pos.x + DROP_COLLECT_RANGE, pos.y + DROP_COLLECT_RANGE, pos.z + DROP_COLLECT_RANGE
        )
        world.getEntitiesWithinAABB(EntityItem::class.java, dropBox).forEach { item ->
            if (!item.isDead && player.inventory.addItemStackToInventory(item.item)) {
                item.isDead = true
            }
        }
        val aoeBox = AxisAlignedBB(
            pos.x - AOE_RANGE, pos.y - 1, pos.z - AOE_RANGE,
            pos.x + AOE_RANGE, pos.y + 2, pos.z + AOE_RANGE
        )
        world.getEntitiesWithinAABB(EntityLivingBase::class.java, aoeBox)
            .filter { it != player && it != target && it.isEntityAlive }
            .forEach { entity ->
                if (entity is EntityDragon) {
                    killDragon(entity, world, player)
                } else {
                    entity.health = 0f
                    entity.isDead = true
                    entity.onDeath(DamageSource.causePlayerDamage(player))
                }
            }
        if (world is WorldServer) {
            spawnDeathParticles(world, pos)
        }
        world.playSound(null,
            target.posX, target.posY, target.posZ,
            SoundEvents.ENTITY_GENERIC_EXPLODE,
            SoundCategory.PLAYERS, 1.0f, 0.8f
        )
    }

    @JvmStatic
    fun fireLaser(player: EntityPlayer, direction: Vec3d, world: World) {
        if (world.isRemote) return
        val start = player.getPositionEyes(1f)
        val end = start.add(direction.scale(LASER_MAX_DISTANCE))
        val blockResult = world.rayTraceBlocks(start, end, false, true, false)
        val actualEnd = blockResult?.hitVec ?: end

        val step = LASER_STEP
        val distance = start.distanceTo(actualEnd)
        val steps = (distance / step).toInt()
        val processedEntities = mutableSetOf<EntityLivingBase>()
        var anyHit = false

        for (i in 0..steps) {
            val t = i.toDouble() / steps
            val pos = start.add((actualEnd.x - start.x) * t, (actualEnd.y - start.y) * t, (actualEnd.z - start.z) * t)
            val box = AxisAlignedBB(
                pos.x - ENTITY_CHECK_RADIUS, pos.y - ENTITY_CHECK_RADIUS, pos.z - ENTITY_CHECK_RADIUS,
                pos.x + ENTITY_CHECK_RADIUS, pos.y + ENTITY_CHECK_RADIUS, pos.z + ENTITY_CHECK_RADIUS
            )
            val entities = world.getEntitiesWithinAABB(EntityLivingBase::class.java, box)
            for (entity in entities) {
                if (entity == player) continue
                if (entity in processedEntities) continue
                processedEntities.add(entity)
                anyHit = true
                if (entity is EntityDragon) {
                    killDragon(entity, world, player)
                } else {
                    entity.health = 0f
                    entity.isDead = true
                    entity.onDeath(DamageSource.causePlayerDamage(player))
                }
                if (world is WorldServer) {
                    world.playSound(null, entity.posX, entity.posY, entity.posZ,
                        SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1f, 0.8f)
                    spawnLaserHitParticles(world, entity.positionVector)
                }
            }
        }

        if (world is WorldServer) {
            spawnLaserParticles(world, start, actualEnd, anyHit)
        }
    }

    private fun killDragon(dragon: EntityDragon, world: World, player: EntityPlayer) {
        if (world.isRemote) return
        dragon.health = 0f
        dragon.onDeath(DamageSource.causePlayerDamage(player))
        dragon.deathTime = 199
        dragon.phaseManager.setPhase(PhaseList.DYING)
        dragon.fightManager?.processDragonDeath(dragon)
        dragon.isDead = true
        world.removeEntityDangerously(dragon)
        if (world is WorldServer) {
            spawnDeathParticles(world, dragon.positionVector)
            world.playSound(null, dragon.posX, dragon.posY, dragon.posZ,
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.0f, 0.8f)
        }
    }


    internal fun spawnDeathParticles(world: WorldServer, pos: Vec3d) {
        val rand = Random()

        for (i in 0..39) {
            val angle = i * (2 * Math.PI / 40) + rand.nextDouble() * 0.3
            val radius = 0.8 + rand.nextDouble() * 0.6
            val xOff = cos(angle) * radius
            val zOff = sin(angle) * radius
            val yOff = i * 0.08 + rand.nextDouble() * 0.2
            world.spawnParticle(
                EnumParticleTypes.END_ROD,
                pos.x + xOff, pos.y + yOff, pos.z + zOff,
                0, 0.0, 0.0, 0.0, 0.0
            )
        }

        repeat(40) {
            val xOff = (rand.nextDouble() - 0.5) * 3.5
            val yOff = rand.nextDouble() * 2.5
            val zOff = (rand.nextDouble() - 0.5) * 3.5
            world.spawnParticle(
                EnumParticleTypes.DRAGON_BREATH,
                pos.x + xOff, pos.y + yOff, pos.z + zOff,
                0, 0.0, 0.0, 0.0, 0.0
            )
        }

        repeat(60) {
            val xOff = (rand.nextDouble() - 0.5) * 4.5
            val yOff = rand.nextDouble() * 3.0 + 0.5
            val zOff = (rand.nextDouble() - 0.5) * 4.5
            world.spawnParticle(
                EnumParticleTypes.ENCHANTMENT_TABLE,
                pos.x + xOff, pos.y + yOff, pos.z + zOff,
                0, 0.0, 0.0, 0.0, 0.0
            )
        }

        repeat(50) {
            val xOff = (rand.nextDouble() - 0.5) * 2.5
            val yOff = rand.nextDouble() * 2.0
            val zOff = (rand.nextDouble() - 0.5) * 2.5
            world.spawnParticle(
                EnumParticleTypes.FIREWORKS_SPARK,
                pos.x + xOff, pos.y + yOff, pos.z + zOff,
                0, 0.0, 0.0, 0.0, 0.0
            )
        }

        repeat(30) {
            val angle = rand.nextDouble() * 2 * Math.PI
            val radius = 1.2 + rand.nextDouble() * 1.2
            val xOff = cos(angle) * radius
            val zOff = sin(angle) * radius
            val yOff = rand.nextDouble() * 1.5
            world.spawnParticle(
                EnumParticleTypes.NOTE,
                pos.x + xOff, pos.y + yOff + 0.5, pos.z + zOff,
                0, rand.nextDouble(), rand.nextDouble(), rand.nextDouble(), 0.0
            )
        }
    }

    private fun spawnLaserParticles(world: WorldServer, start: Vec3d, end: Vec3d, hit: Boolean) {
        val rand = Random()
        val distance = start.distanceTo(end)
        val steps = (distance / LASER_STEP).toInt()

        for (i in 0..steps) {
            val t = i.toDouble() / steps
            val pos = start.add((end.x - start.x) * t, (end.y - start.y) * t, (end.z - start.z) * t)

            val r = rand.nextDouble()
            val g = rand.nextDouble()
            val b = rand.nextDouble()
            world.spawnParticle(
                EnumParticleTypes.SPELL_MOB,
                pos.x, pos.y, pos.z,
                1, r, g, b, 0.0
            )

            if (i % 3 == 0) {
                world.spawnParticle(
                    EnumParticleTypes.END_ROD,
                    pos.x, pos.y, pos.z,
                    0, 0.0, 0.0, 0.0, 0.0
                )
            }

            if (i % 6 == 0) {
                world.spawnParticle(
                    EnumParticleTypes.FIREWORKS_SPARK,
                    pos.x, pos.y, pos.z,
                    0, 0.0, 0.0, 0.0, 0.0
                )
            }

            if (i % 8 == 0) {
                world.spawnParticle(
                    EnumParticleTypes.ENCHANTMENT_TABLE,
                    pos.x + (rand.nextDouble() - 0.5) * 0.3,
                    pos.y + (rand.nextDouble() - 0.5) * 0.3 + 0.2,
                    pos.z + (rand.nextDouble() - 0.5) * 0.3,
                    1, rand.nextDouble() * 0.1, rand.nextDouble() * 0.1, rand.nextDouble() * 0.1, 0.0
                )
            }
        }

        if (hit) {
            spawnLaserHitParticles(world, end)
        }
    }

    private fun spawnLaserHitParticles(world: WorldServer, pos: Vec3d) {
        val rand = Random()

        for (i in 0..29) {
            val angle = i * (2 * Math.PI / 30) + rand.nextDouble() * 0.3
            val radius = 0.5 + rand.nextDouble() * 1.2
            val xOff = cos(angle) * radius
            val zOff = sin(angle) * radius
            val yOff = rand.nextDouble() * 0.5
            world.spawnParticle(
                EnumParticleTypes.END_ROD,
                pos.x + xOff, pos.y + yOff, pos.z + zOff,
                0, 0.0, 0.0, 0.0, 0.0
            )
        }

        for (i in 0..39) {
            val angle = i * (2 * Math.PI / 40) + rand.nextDouble() * 0.3
            val radius = 0.8 + rand.nextDouble() * 1.5
            val xOff = cos(angle) * radius
            val zOff = sin(angle) * radius
            val yOff = rand.nextDouble() * 0.8
            world.spawnParticle(
                EnumParticleTypes.FIREWORKS_SPARK,
                pos.x + xOff, pos.y + yOff, pos.z + zOff,
                0, 0.0, 0.0, 0.0, 0.0
            )
        }

        repeat(30) {
            val xOff = (rand.nextDouble() - 0.5) * 2.0
            val yOff = rand.nextDouble() * 1.5
            val zOff = (rand.nextDouble() - 0.5) * 2.0
            world.spawnParticle(
                EnumParticleTypes.ENCHANTMENT_TABLE,
                pos.x + xOff, pos.y + yOff, pos.z + zOff,
                0, 0.0, 0.0, 0.0, 0.0
            )
        }

        repeat(20) {
            val angle = rand.nextDouble() * 2 * Math.PI
            val radius = 0.8 + rand.nextDouble() * 1.0
            val xOff = cos(angle) * radius
            val zOff = sin(angle) * radius
            val yOff = rand.nextDouble() * 0.8
            world.spawnParticle(
                EnumParticleTypes.NOTE,
                pos.x + xOff, pos.y + yOff, pos.z + zOff,
                0, rand.nextDouble(), rand.nextDouble(), rand.nextDouble(), 0.0
            )
        }

        repeat(20) {
            val xOff = (rand.nextDouble() - 0.5) * 1.5
            val yOff = rand.nextDouble() * 1.0
            val zOff = (rand.nextDouble() - 0.5) * 1.5
            world.spawnParticle(
                EnumParticleTypes.SPELL_MOB,
                pos.x + xOff, pos.y + yOff, pos.z + zOff,
                1, rand.nextDouble(), rand.nextDouble(), rand.nextDouble(), 0.0
            )
        }
    }
}