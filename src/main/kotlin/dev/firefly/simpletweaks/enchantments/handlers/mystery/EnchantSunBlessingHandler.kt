package dev.firefly.simpletweaks.enchantments.handlers.mystery

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.WorldSide
import dev.firefly.simpletweaks.compat.event.LivingDeathEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.event.TickEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantItemWeights
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSupportedItemTags
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.damagesource.ModDamageTypes
import dev.firefly.simpletweaks.util.damagesource.damageElemental
import dev.firefly.simpletweaks.util.elements.ElementType
import dev.firefly.simpletweaks.util.elements.ReactionOutcome
import dev.firefly.simpletweaks.util.elements.ReactionSeam
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.particle.ParticleEffect
import net.minecraft.particle.ParticleTypes
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.tag.EntityTypeTags
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.Box
import net.minecraft.world.World
import java.util.UUID
import java.util.WeakHashMap

/**
 * 日之祝福: a share of every **fire / light family** reaction is re-expressed as 日曜伤害.
 *
 * The blessing of the noon sun has no crit and no stacking (that is the moon's identity). Its identity is
 * **afterburn**: +50 % against the undead, and every rider keeps paying out after the hit. It is the most
 * damage-forward of the three blessings — not one rider heals, buffs or shields.
 *
 * | reaction | effect |
 * |---|---|
 * | `sanctify`   | 圣辉: the same instance lands again 1 s later |
 * | `scorch`     | 灼痕: burns for 5 s, 15 %/s |
 * | `annihilate` | 灭度: marked 6 s; if it dies while marked, 60 % bursts over 5 blocks |
 * | `burn`       | 焦土: leaves scorched ground for 6 s, 10 %/s to enemies standing in it |
 * | `extinguish` | 余烬: three sparks over half a second, 50 % each (150 % total) |
 * | `quicken`    | 日冕: opens a 6 s window where every solar instance is ×1.75, this one included |
 *
 * [CORONA_MULTIPLIER] is the only rider that changes the instance; everything else schedules extra damage,
 * so the log number is always the number that lands.
 */
@ModEnchantment(
    id = "sun_blessing",
    category = EnchantCategory.MYSTERY,
    type = EnchantType.WEAPON,
    maxLevel = 3,
    damagePerLevel = 2.5,
    weight = EnchantItemWeights.MYSTERY,
    anvilCost = 20,
    minCostBase = 55,
    supportedItems = EnchantSupportedItemTags.WEAPON,
    slots = [EnchantSlot.MAINHAND],
)
object EnchantSunBlessingHandler : Listenable {

    /** Share of the reaction re-expressed as solar damage: 30 % / 40 % / 50 %. */
    const val SHARE_BASE = 0.20f
    const val SHARE_PER_LEVEL = 0.10f

    /** Daylight hurts the restless dead — the only blessing with a target-type multiplier. */
    const val UNDEAD_BONUS = 1.5f

    /** `sanctify` — 圣辉: the same instance repeats once, one second later. */
    const val ECHO_INTERVAL_TICKS = 20
    private const val ECHO_FIRES = 1

    /** `scorch` — 灼痕: 5 s burn, one pulse per second. */
    const val WICK_INTERVAL_TICKS = 20
    private const val WICK_FIRES = 5
    const val WICK_SHARE = 0.15f

    /** `burn` — 焦土: 6 s of scorched ground, 3 blocks, one pulse per second. */
    const val FIELD_INTERVAL_TICKS = 20
    private const val FIELD_FIRES = 6
    const val FIELD_RADIUS = 3.0
    const val FIELD_SHARE = 0.10f

    /** `extinguish` — 余烬: three sparks in half a second, 50 % each — the dying fire flares up. */
    const val EMBER_INTERVAL_TICKS = 5
    private const val EMBER_FIRES = 3
    const val EMBER_SHARE = 0.50f

    /** `quicken` — 日冕: the sun stands overhead, and everything it touches burns brighter. */
    const val CORONA_TICKS = 120L
    const val CORONA_MULTIPLIER = 1.75f

    /** `annihilate` — 灭度: an on-death burst, the only rider that waits for a kill. */
    const val DOOM_TICKS = 120L
    const val DOOM_SHARE = 0.60f
    const val DOOM_RADIUS = 5.0

    private const val MAX_PULSES = 96

    /**
     * One scheduled pulse. `fires` counts down so echo (1), burn (5) and ground (6) share one loop, and
     * every pulse carries its own dimension so a portal traveller cannot damage the other side.
     */
    private class Pulse(
        val dimension: RegistryKey<World>,
        val ownerUUID: UUID,
        val targetUUID: UUID?,
        val x: Double,
        val y: Double,
        val z: Double,
        val amount: Float,
        val kind: String,
        val interval: Int,
        var fires: Int,
        var delay: Int,
    )

    private class Doom(val expiresAt: Long, val ownerUUID: UUID, val damage: Float)

    private val pulses = mutableListOf<Pulse>()
    private val doom = WeakHashMap<LivingEntity, Doom>()
    private val corona = WeakHashMap<PlayerEntity, Long>()

    /**
     * Fire + light family. Not water (moon) and not frost (star), and `blight` is deliberately left out —
     * rot is the one reaction the sun has no business blessing.
     */
    private val SOLAR_REACTIONS = setOf("sanctify", "scorch", "annihilate", "burn", "extinguish", "quicken")

    /** Namespace installed on the reaction label: `sun:reaction.simple_tweaks.scorch` → 「日灼裂」. */
    const val LABEL_PREFIX = "sun"

    fun shareFor(level: Int): Float = SHARE_BASE + SHARE_PER_LEVEL * level.coerceIn(0, 3)

    /** Keys this blessing answers to — the game test asserts every one of them exists in the table. */
    @JvmStatic
    fun reactionKeys(): Set<String> = SOLAR_REACTIONS

    override fun init() {
        ReactionSeam.register { attacker, target, element, outcome -> onReaction(attacker, target, element, outcome) }
    }

    private fun onReaction(
        attacker: LivingEntity?,
        target: LivingEntity,
        element: ElementType,
        outcome: ReactionOutcome,
    ) {
        val player = attacker as? PlayerEntity ?: return
        if (player.world.isClient) return
        val key = outcome.key ?: return
        if (key !in SOLAR_REACTIONS) return
        val flatDamage = outcome.flatDamage
        val level = getItemSpecificEnchantLevel(player.mainHandStack, GeneratedEnchantments.SUN_BLESSING)
        if (level <= 0 || flatDamage <= 0f) return

        // `quicken` opens the corona window for everything that follows, itself included.
        val now = player.world.time
        if (key == "quicken") corona[player] = now + CORONA_TICKS
        val focused = (corona[player] ?: 0L) > now

        var sun = flatDamage * shareFor(level)
        if (focused) sun *= CORONA_MULTIPLIER
        val undead = target.type.isIn(EntityTypeTags.UNDEAD)
        if (undead) sun *= UNDEAD_BONUS

        target.damageElemental(player, ModDamageTypes.SUN, sun)
        // The reaction stops being "灼裂" and becomes "日灼裂" for this hit.
        outcome.labelPrefix = LABEL_PREFIX

        var detail = ""
        when (key) {
            "sanctify" -> {
                schedule(player, target, sun, "echo", ECHO_INTERVAL_TICKS, ECHO_FIRES)
                spawn(target, ParticleTypes.END_ROD, 10, 0.3, 0.02)
            }
            "scorch" -> {
                schedule(player, target, sun * WICK_SHARE, "wick", WICK_INTERVAL_TICKS, WICK_FIRES)
                spawn(target, ParticleTypes.SMALL_FLAME, 8, 0.3, 0.01)
            }
            "annihilate" -> {
                doom[target] = Doom(player.world.time + DOOM_TICKS, player.uuid, sun * DOOM_SHARE)
                spawn(target, ParticleTypes.ASH, 12, 0.35, 0.02)
                detail = ", marked=${DOOM_TICKS / 20}s"
            }
            "burn" -> {
                schedule(player, target, sun * FIELD_SHARE, "field", FIELD_INTERVAL_TICKS, FIELD_FIRES, follows = false)
                spawn(target, ParticleTypes.FLAME, 14, 0.5, 0.02)
            }
            "extinguish" -> {
                schedule(player, target, sun * EMBER_SHARE, "ember", EMBER_INTERVAL_TICKS, EMBER_FIRES)
                spawn(target, ParticleTypes.LAVA, 8, 0.3, 0.05)
                detail = ", embers=${EMBER_FIRES}x${(EMBER_SHARE * 100).toInt()}%"
            }
            "quicken" -> {
                spawn(player, ParticleTypes.FLAME, 10, 0.35, 0.03)
                detail = ", corona=${CORONA_TICKS / 20}s, focused=$focused"
            }
        }

        FSTLog.log("SunBlessing") {
            "element=${element.id}, lvl=$level, reaction=${"%.2f".format(flatDamage)}, undead=$undead, " +
                "corona=$focused, solar=${"%.2f".format(sun)}, effect=$key$detail, outcome=solar-conversion"
        }
    }

    /** `follows` false parks the pulse at a position instead of following the entity (ground fire). */
    private fun schedule(
        player: PlayerEntity,
        target: LivingEntity,
        amount: Float,
        kind: String,
        interval: Int,
        fires: Int,
        follows: Boolean = true,
    ) {
        if (pulses.size >= MAX_PULSES) pulses.removeAt(0)
        pulses.add(
            Pulse(
                dimension = target.world.registryKey,
                ownerUUID = player.uuid,
                targetUUID = if (follows) target.uuid else null,
                x = target.x,
                y = target.y,
                z = target.z,
                amount = amount,
                kind = kind,
                interval = interval,
                fires = fires,
                delay = interval,
            )
        )
    }

    /**
     * Delayed riders are world ticks, not scheduler tasks: a target that leaves the loaded area simply stops
     * matching, and a pulse can never fire in another dimension.
     */
    @SubscribeEvent
    fun onWorldTick(e: TickEvent.WorldTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        val world = e.world
        if (WorldSide.isClient(world)) return
        if (pulses.isEmpty()) return
        val serverWorld = world as? ServerWorld ?: return
        val dimension = world.registryKey

        val iterator = pulses.iterator()
        while (iterator.hasNext()) {
            val pulse = iterator.next()
            if (pulse.dimension != dimension) continue
            pulse.delay--
            if (pulse.delay > 0) continue
            pulse.fires--
            if (pulse.fires <= 0) iterator.remove() else pulse.delay = pulse.interval
            fire(serverWorld, pulse)
        }
    }

    private fun fire(world: ServerWorld, pulse: Pulse) {
        val owner = world.getPlayerByUuid(pulse.ownerUUID)
        if (pulse.kind == "field") {
            val box = Box(
                pulse.x - FIELD_RADIUS, pulse.y - FIELD_RADIUS, pulse.z - FIELD_RADIUS,
                pulse.x + FIELD_RADIUS, pulse.y + FIELD_RADIUS, pulse.z + FIELD_RADIUS,
            )
            var hits = 0
            for (other in world.getOtherEntities(null, box) { it is LivingEntity && it.isAlive && it !== owner }) {
                (other as LivingEntity).damageElemental(owner, ModDamageTypes.SUN, pulse.amount)
                hits++
            }
            world.spawnParticles(ParticleTypes.FLAME, pulse.x, pulse.y + 0.2, pulse.z, 10, 2.4, 0.2, 2.4, 0.01)
            if (hits > 0) {
                FSTLog.log("SunBlessing") {
                    "owner=${owner?.name?.string ?: "?"}, field=${"%.2f".format(pulse.amount)}, " +
                        "hits=$hits, outcome=field-burned"
                }
            }
            return
        }

        val targetUUID = pulse.targetUUID ?: return
        val target = world.getEntity(targetUUID) as? LivingEntity ?: return
        if (!target.isAlive) return
        target.damageElemental(owner, ModDamageTypes.SUN, pulse.amount)
        val particle = when (pulse.kind) {
            "wick" -> ParticleTypes.SMALL_FLAME
            "ember" -> ParticleTypes.LAVA
            else -> ParticleTypes.END_ROD
        }
        world.spawnParticles(
            particle,
            target.x, target.y + target.height * 0.5, target.z,
            8, 0.3, 0.3, 0.3, 0.02,
        )
        FSTLog.log("SunBlessing") {
            "owner=${owner?.name?.string ?: "?"}, target=${target.name.string}, kind=${pulse.kind}, " +
                "damage=${"%.2f".format(pulse.amount)}, outcome=pulse-landed"
        }
    }

    /** `annihilate`: the mark only pays out on a kill, which is why it is the one event-driven rider. */
    @SubscribeEvent
    fun onLivingDeath(e: LivingDeathEvent) {
        val victim = e.entityLiving
        if (victim.world.isClient) return
        val mark = doom.remove(victim) ?: return
        if (mark.expiresAt <= victim.world.time) return
        val world = victim.world as? ServerWorld ?: return
        val owner = world.getPlayerByUuid(mark.ownerUUID)
        val box = Box(
            victim.x - DOOM_RADIUS, victim.y - DOOM_RADIUS, victim.z - DOOM_RADIUS,
            victim.x + DOOM_RADIUS, victim.y + DOOM_RADIUS, victim.z + DOOM_RADIUS,
        )
        var hits = 0
        for (other in world.getOtherEntities(victim, box) { it is LivingEntity && it.isAlive && it !== owner }) {
            (other as LivingEntity).damageElemental(owner, ModDamageTypes.SUN, mark.damage)
            hits++
        }
        world.spawnParticles(ParticleTypes.EXPLOSION, victim.x, victim.y + 0.5, victim.z, 4, 1.6, 0.4, 1.6, 0.0)
        FSTLog.log("SunBlessing") {
            "victim=${victim.name.string}, doom=${"%.2f".format(mark.damage)}, hits=$hits, outcome=doom-burst"
        }
    }

    private fun spawn(target: LivingEntity, particle: ParticleEffect, count: Int, spread: Double, speed: Double) {
        val world = target.world as? ServerWorld ?: return
        world.spawnParticles(particle, target.x, target.y + target.height * 0.5, target.z, count, spread, spread, spread, speed)
    }
}
