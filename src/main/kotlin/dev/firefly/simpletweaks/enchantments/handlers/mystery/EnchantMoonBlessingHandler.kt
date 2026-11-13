package dev.firefly.simpletweaks.enchantments.handlers.mystery

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.WorldSide
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
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.particle.ParticleTypes
import net.minecraft.registry.RegistryKey
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.Box
import net.minecraft.world.World
import java.util.UUID
import java.util.WeakHashMap

/**
 * 月之祝福: a share of every **water-family** reaction you trigger is re-expressed as 月曜伤害, which can
 * crit and is amplified by 月华 stacks.
 *
 * Each of the four reactions also does something of its own, so the enchantment is not "bigger number ×4":
 * | reaction | effect |
 * |---|---|
 * | `steam`   | 月雾: the target takes +25 % lunar damage for 6 s (this hit included if already misted) |
 * | `conduct` | 月弧: 40 % of the lunar instance arcs to the nearest other enemy within 6 blocks |
 * | `bloom`   | 月华之种: blooms after 1.5 s for 3 blocks, then heals the caster 20 % of it |
 * | `purify`  | 月净: strips one debuff from the caster; with no debuff the instance is ×1.5 |
 *
 * The two that change the instance itself run **before** the damage lands, so the number in the log is the
 * number that hits; everything else is a side effect and runs after.
 */
@ModEnchantment(
    id = "moon_blessing",
    category = EnchantCategory.MYSTERY,
    type = EnchantType.WEAPON,
    maxLevel = 3,
    weight = EnchantItemWeights.MYSTERY,
    anvilCost = 20,
    minCostBase = 55,
    supportedItems = EnchantSupportedItemTags.WEAPON,
    slots = [EnchantSlot.MAINHAND],
)
object EnchantMoonBlessingHandler : Listenable {

    /** Share of the reaction re-expressed as lunar damage: 30 % / 40 % / 50 %. */
    const val SHARE_BASE = 0.20f
    const val SHARE_PER_LEVEL = 0.10f

    /** Crit chance and multiplier of the lunar instance. */
    const val CRIT_BASE = 0.15f
    const val CRIT_PER_LEVEL = 0.10f
    const val CRIT_MULTIPLIER = 1.5f

    /** 月华: one stack per conversion, each +5 % lunar damage. */
    const val STACK_BONUS = 0.05f
    const val MAX_STACKS = 5
    const val STACK_TICKS = 200L

    /** `steam` — 月雾: lunar damage taken is multiplied while the mist lasts. */
    const val MIST_BONUS = 1.25f
    const val MIST_TICKS = 120L

    /** `conduct` — 月弧: one hop to the nearest other enemy, no chaining. */
    const val ARC_SHARE = 0.40f
    const val ARC_RADIUS = 6.0

    /** `bloom` — 月华之种: delayed area bloom that pays the caster back. */
    const val SEED_DELAY_TICKS = 30
    const val SEED_SHARE = 0.40f
    const val SEED_RADIUS = 3.0
    const val SEED_HEAL_SHARE = 0.20f
    private const val MAX_SEEDS = 64

    /** `purify` — 月净: pay-off for having nothing to strip. */
    const val CLEANSE_BONUS = 1.5f

    private class Stacks(var count: Int, var expiresAt: Long)

    private class Seed(
        val x: Double,
        val y: Double,
        val z: Double,
        val dimension: RegistryKey<World>,
        val ownerUUID: UUID,
        val damage: Float,
        var ticksLeft: Int,
    )

    private val STACKS = WeakHashMap<PlayerEntity, Stacks>()
    private val MIST = WeakHashMap<LivingEntity, Long>()
    private val seeds = mutableListOf<Seed>()

    /**
     * Only **water-driven** reactions can go lunar (Genshin's 月曜反应 are the 水 family: 蒸发/感电/绽放).
     * A fire-vs-frost reaction has nothing to do with the moon.
     */
    private val LUNAR_REACTIONS = setOf("steam", "conduct", "bloom", "purify")

    /** Namespace installed on the reaction label, e.g. `moon:reaction.simple_tweaks.conduct` → 「月感电」. */
    const val LABEL_PREFIX = "moon"

    fun shareFor(level: Int): Float = SHARE_BASE + SHARE_PER_LEVEL * level.coerceIn(0, 3)

    fun critChanceFor(level: Int): Float = CRIT_BASE + CRIT_PER_LEVEL * level.coerceIn(0, 3)

    /** Keys this blessing answers to — the game test asserts every one of them exists in the table. */
    @JvmStatic
    fun reactionKeys(): Set<String> = LUNAR_REACTIONS

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
        if (key !in LUNAR_REACTIONS) return
        val flatDamage = outcome.flatDamage
        val level = getItemSpecificEnchantLevel(player.mainHandStack, GeneratedEnchantments.MOON_BLESSING)
        if (level <= 0 || flatDamage <= 0f) return

        val now = player.world.time
        val stacks = STACKS[player]
        val count = if (stacks != null && stacks.expiresAt > now) stacks.count else 0
        val multiplier = 1f + STACK_BONUS * count
        val crit = player.random.nextFloat() < critChanceFor(level)
        var lunar = flatDamage * shareFor(level) * multiplier * (if (crit) CRIT_MULTIPLIER else 1f)

        // Instance modifiers first: what the log prints must be what lands.
        val canCleanse = key == "purify" && firstHarmfulEffect(player) == null
        when (key) {
            "steam" -> if (isMisted(target, now)) lunar *= MIST_BONUS
            "purify" -> if (canCleanse) lunar *= CLEANSE_BONUS
        }

        target.damageElemental(player, ModDamageTypes.MOON, lunar)
        STACKS[player] = Stacks(minOf(count + 1, MAX_STACKS), now + STACK_TICKS)
        // The reaction stops being "过载" and becomes "月过载" for this hit.
        outcome.labelPrefix = LABEL_PREFIX

        var detail = ""
        when (key) {
            "steam" -> {
                MIST[target] = now + MIST_TICKS
                spawn(target, ParticleTypes.CLOUD, 10, 0.4, 0.01)
            }
            "conduct" -> {
                val arc = arc(player, target, lunar)
                detail = ", arc=${arc?.let { "%.2f".format(lunar * ARC_SHARE) } ?: "none"}"
            }
            "bloom" -> {
                plantSeed(player, target, lunar)
                spawn(target, ParticleTypes.HAPPY_VILLAGER, 6, 0.3, 0.01)
            }
            "purify" -> {
                val cleansed = cleanse(player)
                detail = ", cleansed=${cleansed?.effectType?.value()?.name?.string ?: "none"}"
            }
        }

        FSTLog.log("MoonBlessing") {
            "element=${element.id}, lvl=$level, reaction=${"%.2f".format(flatDamage)}, " +
                "stacks=$count, crit=$crit, lunar=${"%.2f".format(lunar)}, effect=$key$detail, " +
                "outcome=lunar-conversion"
        }
    }

    /** `conduct`: one hop to the closest *other* enemy — deliberately not a chain. */
    private fun arc(player: PlayerEntity, target: LivingEntity, lunar: Float): LivingEntity? {
        var best: LivingEntity? = null
        var bestDistance = ARC_RADIUS * ARC_RADIUS
        val box = target.boundingBox.expand(ARC_RADIUS)
        for (other in player.world.getOtherEntities(target, box) { it is LivingEntity && it.isAlive }) {
            val living = other as LivingEntity
            if (living === player) continue
            val distance = living.squaredDistanceTo(target)
            if (distance >= bestDistance) continue
            bestDistance = distance
            best = living
        }
        val victim = best ?: return null
        victim.damageElemental(player, ModDamageTypes.MOON, lunar * ARC_SHARE)
        spawn(victim, ParticleTypes.ELECTRIC_SPARK, 8, 0.3, 0.05)
        return victim
    }

    private fun plantSeed(player: PlayerEntity, target: LivingEntity, lunar: Float) {
        if (seeds.size >= MAX_SEEDS) seeds.removeAt(0)
        seeds.add(
            Seed(
                x = target.x,
                y = target.y,
                z = target.z,
                dimension = target.world.registryKey,
                ownerUUID = player.uuid,
                damage = lunar * SEED_SHARE,
                ticksLeft = SEED_DELAY_TICKS,
            )
        )
    }

    /** `bloom`: the seed is a world tick, not a scheduler task — it has to survive a chunk-less target. */
    @SubscribeEvent
    fun onWorldTick(e: TickEvent.WorldTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        val world = e.world
        if (WorldSide.isClient(world)) return
        if (seeds.isEmpty()) return

        val dimension = world.registryKey
        val toBloom = mutableListOf<Seed>()
        val iterator = seeds.iterator()
        while (iterator.hasNext()) {
            val seed = iterator.next()
            if (seed.dimension != dimension) continue
            seed.ticksLeft--
            if (seed.ticksLeft <= 0) {
                toBloom.add(seed)
                iterator.remove()
            }
        }
        if (toBloom.isEmpty()) return

        val serverWorld = world as? ServerWorld ?: return
        for (seed in toBloom) {
            val owner = world.getPlayerByUuid(seed.ownerUUID)
            val box = Box(
                seed.x - SEED_RADIUS, seed.y - SEED_RADIUS, seed.z - SEED_RADIUS,
                seed.x + SEED_RADIUS, seed.y + SEED_RADIUS, seed.z + SEED_RADIUS,
            )
            var hits = 0
            for (other in serverWorld.getOtherEntities(null, box) { it is LivingEntity && it.isAlive && it !== owner }) {
                (other as LivingEntity).damageElemental(owner, ModDamageTypes.MOON, seed.damage)
                hits++
            }
            serverWorld.spawnParticles(
                ParticleTypes.HAPPY_VILLAGER,
                seed.x, seed.y + 0.5, seed.z,
                16, 1.4, 0.3, 1.4, 0.02,
            )
            val healed = if (owner != null && hits > 0) {
                val amount = seed.damage * SEED_HEAL_SHARE * hits
                owner.heal(amount)
                amount
            } else {
                0f
            }
            FSTLog.log("MoonBlessing") {
                "owner=${owner?.name?.string ?: "?"}, bloom=${"%.2f".format(seed.damage)}, " +
                    "hits=$hits, healed=${"%.2f".format(healed)}, outcome=seed-bloomed"
            }
        }
    }

    private fun isMisted(target: LivingEntity, now: Long): Boolean {
        val until = MIST[target] ?: return false
        if (until <= now) {
            MIST.remove(target)
            return false
        }
        return true
    }

    private fun firstHarmfulEffect(player: PlayerEntity): StatusEffectInstance? =
        player.statusEffects.firstOrNull { !it.effectType.value().isBeneficial }

    private fun cleanse(player: PlayerEntity): StatusEffectInstance? {
        val effect = firstHarmfulEffect(player) ?: return null
        player.removeStatusEffect(effect.effectType)
        return effect
    }

    private fun spawn(target: LivingEntity, particle: net.minecraft.particle.ParticleEffect, count: Int, spread: Double, speed: Double) {
        val world = target.world as? ServerWorld ?: return
        world.spawnParticles(particle, target.x, target.y + target.height * 0.5, target.z, count, spread, spread, spread, speed)
    }

    @JvmStatic
    fun stacksOf(player: PlayerEntity): Int = STACKS[player]?.count ?: 0

    @JvmStatic
    fun clear(player: PlayerEntity) {
        STACKS.remove(player)
    }
}
