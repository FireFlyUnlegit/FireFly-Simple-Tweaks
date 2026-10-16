package dev.firefly.simpletweaks.enchantments.handlers.rare

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.WorldSide
import dev.firefly.simpletweaks.compat.attacker
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.event.TickEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantItemWeights
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSupportedItemTags
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.damagesource.ElementDamageTypes
import dev.firefly.simpletweaks.util.damagesource.damageElemental
import dev.firefly.simpletweaks.util.damagesource.isElementDamage
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.passive.TameableEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.util.math.Box
import java.lang.ref.WeakReference
import java.util.UUID

/**
 * **Chain Lightning**（连锁闪电） — the lightning third of the elemental trio: the first hit charges
 * the target, the next hit arcs lightning into its neighbours.
 *
 * ## The rule as implemented (the author's formulas, in one table)
 * | quantity | formula | at level 3 |
 * |---|---|---|
 * | arc damage, per target | `hitDamage x (0.3 + 0.1 x level)` | `0.6 x` |
 * | arc range | `3 + level` blocks | 6 blocks |
 * | arc targets | `1 + level / 2` (integer division) | 2 |
 * | charge window | `60 + 40 x level` ticks | 180 ticks (9 s) |
 *
 * ## Behaviour notes that are decisions, not transcriptions
 *  - **The arc does not re-charge its victims.** A charged victim would arc again on the next hit,
 *    which turns any group fight into a cascade whose size is `targets ^ hits`. The arc is also an
 *    [isElementDamage] hit, so this handler ignores it by construction — the explicit decision is to
 *    *not* write a mark for arc victims, and it is recorded here so nobody "fixes" it later.
 *  - **The arc damage respects armour.** Unlike venom, the author's formula says nothing about
 *    ignoring anything, so `simple_tweaks:chain_lightning` is in no bypass tag.
 *  - **Ally filter.** The arc skips the wearer, their scoreboard teammates and their tamed mobs.
 *    1.21's `isTeammate` is scoreboard-only, so the `TameableEntity#isOwner` clause is needed
 *    explicitly — the same correction `starfall` carries.
 *
 * ## Guards
 *  - `isElementDamage()`: without it, the arc's own damage would be a "hit" that either charges or
 *    chains, and a chain would chain again.
 *  - `attacker === target`: self-inflicted damage must not charge.
 */
@ModEnchantment(
    id = "chain_lightning",
    category = EnchantCategory.RARE,
    type = EnchantType.SWORD,
    maxLevel = 3,
    weight = EnchantItemWeights.RARE,
    anvilCost = 6,
    minCostBase = 24,
    minCostPerLevel = 4,
    supportedItems = EnchantSupportedItemTags.SWORD,
    slots = [EnchantSlot.MAINHAND],
    damagePerLevel = 0.0,
    order = 55,
)
object EnchantChainLightningHandler : Listenable {

    /** Arc damage as a fraction of the triggering hit: `0.3 + 0.1 x level`. */
    private const val ARC_DAMAGE_BASE = 0.3f
    private const val ARC_DAMAGE_PER_LEVEL = 0.1f

    /** Arc range in blocks: `3 + level`. */
    private const val ARC_RANGE_BASE = 3.0

    private class Charge(val ref: WeakReference<LivingEntity>, var expireAtMs: Long)

    private val charges = mutableMapOf<UUID, Charge>()

    @SubscribeEvent
    fun onHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        if (e.source.isElementDamage()) return

        val target = e.entityLiving ?: return
        val attacker = e.attacker as? PlayerEntity ?: return
        if (attacker === target) return

        val lvl = getItemSpecificEnchantLevel(attacker.mainHandStack, GeneratedEnchantments.CHAIN_LIGHTNING)
        if (lvl <= 0) return

        val now = System.currentTimeMillis()
        val existing = charges[target.uuid]
        val charged = existing != null && existing.ref.get() === target && now <= existing.expireAtMs
        if (existing != null && !charged) charges.remove(target.uuid)

        if (charged) {
            charges.remove(target.uuid)
            val hitDamage = e.amount
            triggerArc(attacker, target, lvl, hitDamage)
            return
        }

        val windowMs = (60 + 40 * lvl) * 50L
        charges[target.uuid] = Charge(WeakReference(target), now + windowMs)
        spawnChargeFx(target)
        FSTLog.log("ChainLightning") {
            "attacker=${attacker.name.string}, target=${target.name.string}, lvl=$lvl, " +
                "windowMs=$windowMs, outcome=charged"
        }
    }

    /**
     * Arcs lightning from [origin] into the nearest enemies within range.
     *
     * Called with `charges[origin]` already removed: the damage handed out here is the trio's own
     * type, so it cannot re-enter [onHurt], but removing first also keeps the state consistent if a
     * victim dies inside this call and [onHurt] runs for something else in between.
     */
    private fun triggerArc(attacker: PlayerEntity, origin: LivingEntity, lvl: Int, hitDamage: Float) {
        val range = ARC_RANGE_BASE + lvl
        val arcDamage = hitDamage * (ARC_DAMAGE_BASE + ARC_DAMAGE_PER_LEVEL * lvl)
        val maxTargets = 1 + lvl / 2

        val box = Box(
            origin.x - range, origin.y - range, origin.z - range,
            origin.x + range, origin.y + range, origin.z + range,
        )

        val victims = origin.world
            .getEntitiesByClass(LivingEntity::class.java, box) { it.isAlive }
            .filter { it !== origin && it !== attacker }
            .filter { !it.isTeammate(attacker) }
            .filter { it !is TameableEntity || !it.isOwner(attacker) }
            .filter { it.squaredDistanceTo(origin) <= range * range }
            .sortedBy { it.squaredDistanceTo(origin) }
            .take(maxTargets)

        val world = origin.world as? ServerWorld
        world?.spawnParticles(
            ParticleTypes.ELECTRIC_SPARK,
            origin.x, origin.y + origin.height * 0.6, origin.z,
            20, 0.3, 0.5, 0.3, 0.1,
        )
        world?.playSound(
            null, origin.x, origin.y, origin.z,
            SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 0.4f, 1.7f,
        )

        var landed = 0
        for (victim in victims) {
            val applied = victim.damageElemental(attacker, ElementDamageTypes.CHAIN_LIGHTNING, arcDamage)
            if (applied) landed++
            drawArc(world, origin, victim)
        }

        FSTLog.log("ChainLightning") {
            "attacker=${attacker.name.string}, origin=${origin.name.string}, lvl=$lvl, hitDamage=$hitDamage, " +
                "arcDamage=$arcDamage, range=$range, maxTargets=$maxTargets, " +
                "candidates=${victims.size}, landed=$landed, " +
                "victims=${victims.joinToString(",") { it.name.string }}, outcome=arc"
        }
    }

    /** Draws the arc itself, so the chain is visible even when a victim is off-camera at the origin. */
    private fun drawArc(world: ServerWorld?, from: LivingEntity, to: LivingEntity) {
        if (world == null) return
        val steps = 8
        val x0 = from.x
        val y0 = from.y + from.height * 0.6
        val z0 = from.z
        val x1 = to.x
        val y1 = to.y + to.height * 0.6
        val z1 = to.z
        for (i in 1..steps) {
            val t = i.toDouble() / steps
            world.spawnParticles(
                ParticleTypes.ELECTRIC_SPARK,
                x0 + (x1 - x0) * t,
                y0 + (y1 - y0) * t,
                z0 + (z1 - z0) * t,
                1, 0.05, 0.05, 0.05, 0.0,
            )
        }
    }

    /** Drops charges on entities that are gone or whose window has run out (map growth guard). */
    @SubscribeEvent
    fun onWorldTick(e: TickEvent.WorldTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        if (WorldSide.isClient(e.world)) return
        if (charges.isEmpty()) return

        val now = System.currentTimeMillis()
        val iter = charges.entries.iterator()
        while (iter.hasNext()) {
            val (_, charge) = iter.next()
            val entity = charge.ref.get()
            if (entity == null || entity.isRemoved || !entity.isAlive || now > charge.expireAtMs) {
                iter.remove()
            }
        }
    }

    private fun spawnChargeFx(target: LivingEntity) {
        val world = target.world as? ServerWorld ?: return
        world.spawnParticles(
            ParticleTypes.ELECTRIC_SPARK,
            target.x, target.y + target.height * 0.7, target.z,
            8, 0.3, 0.4, 0.3, 0.05,
        )
    }
}
