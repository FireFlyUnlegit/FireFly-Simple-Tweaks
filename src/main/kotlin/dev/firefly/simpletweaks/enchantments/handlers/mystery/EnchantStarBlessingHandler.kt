package dev.firefly.simpletweaks.enchantments.handlers.mystery

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantItemWeights
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSupportedItemTags
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.HealLock
import dev.firefly.simpletweaks.util.absorptionHeadroomId
import dev.firefly.simpletweaks.util.damagesource.ModDamageTypes
import dev.firefly.simpletweaks.util.damagesource.damageElemental
import dev.firefly.simpletweaks.util.damagesource.isAuxiliaryDamage
import dev.firefly.simpletweaks.util.elements.ElementType
import dev.firefly.simpletweaks.util.elements.ReactionOutcome
import dev.firefly.simpletweaks.util.elements.ReactionSeam
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.grantAbsorption
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.particle.ParticleEffect
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.world.ServerWorld
import java.util.WeakHashMap

/**
 * 星之祝福: a share of every **frost-family** reaction you trigger becomes 星曜伤害, which always crits and
 * splashes to nearby enemies. The author's reference is Genshin's star reactions: crit, and it spreads.
 *
 * On top of the splash, each reaction gets its own effect:
 * | reaction | effect |
 * |---|---|
 * | `melt`    | 星熔: the instance itself bursts for ×1.8 |
 * | `vanish`  | 星隐: a shield worth 100 % of the instance |
 * | `overload`| 星爆: 35 % of the instance to everything within 4 blocks, knocked back |
 * | `eclipse` | 星蚀: steal one buff from the target; with no buff the instance is ×1.3 |
 * | `freeze`  | 星封: 3 s heal lock (the reaction already freezes) |
 * | `brittle` | 星裂: +10 % from every non-rider hit for 5 s |
 */
@ModEnchantment(
    id = "star_blessing",
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
object EnchantStarBlessingHandler : Listenable {

    /** Share of the reaction re-expressed as star damage: 25 % / 35 % / 45 %. */
    const val SHARE_BASE = 0.15f
    const val SHARE_PER_LEVEL = 0.10f

    /** Star instances always crit; the multiplier is fixed. */
    const val CRIT_MULTIPLIER = 1.5f

    /** Splash: radius and share, so the star is the area option while the moon is the stacking one. */
    const val SPLASH_RADIUS = 4.0
    const val SPLASH_SHARE = 0.5f

    /** `melt` — 星熔: one big instance instead of any rider. */
    const val MELT_BURST = 1.8f

    /** `vanish` — 星隐: absorption worth the whole instance. */
    const val VEIL_SHIELD_SHARE = 1.0f
    private const val VEIL_SOURCE = "star_veil"

    /** `overload` — 星爆: area detonation around the target. */
    const val DETONATE_RADIUS = 4.0
    const val DETONATE_SHARE = 0.35f
    private const val DETONATE_KNOCKBACK = 0.45

    /** `eclipse` — 星蚀: pay-off for a target with nothing to steal. */
    const val ECLIPSE_BONUS = 1.3f

    /** `freeze` — 星封: heal lock, in ticks. */
    const val SEAL_TICKS = 60L

    /** `brittle` — 星裂: the target takes more from real hits. */
    const val FRACTURE_BONUS = 0.10f
    const val FRACTURE_TICKS = 100L

    private val FRACTURE = WeakHashMap<LivingEntity, Long>()

    fun shareFor(level: Int): Float = SHARE_BASE + SHARE_PER_LEVEL * level.coerceIn(0, 3)

    /**
     * Only **frost-driven** reactions can go stellar (Genshin's 星烁反应 are the 冰 family: 星超导/星扩散).
     * The moon owns the water family; the two must not overlap.
     */
    private val STELLAR_REACTIONS = setOf("melt", "vanish", "overload", "eclipse", "freeze", "brittle")

    /** Namespace installed on the reaction label, e.g. `star:reaction.simple_tweaks.overload` → 「星过载」. */
    const val LABEL_PREFIX = "star"

    /** Keys this blessing answers to — the game test asserts every one of them exists in the table. */
    @JvmStatic
    fun reactionKeys(): Set<String> = STELLAR_REACTIONS

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
        if (key !in STELLAR_REACTIONS) return
        val flatDamage = outcome.flatDamage
        val level = getItemSpecificEnchantLevel(player.mainHandStack, GeneratedEnchantments.STAR_BLESSING)
        if (level <= 0 || flatDamage <= 0f) return

        var star = flatDamage * shareFor(level) * CRIT_MULTIPLIER

        // 星蚀 looks at the target before it is hit: a buff stolen is worth more than the flat bonus.
        val stolen = if (key == "eclipse") firstBeneficialEffect(target) else null
        when (key) {
            "melt" -> star *= MELT_BURST
            "eclipse" -> if (stolen == null) star *= ECLIPSE_BONUS
        }

        target.damageElemental(player, ModDamageTypes.STAR, star)
        // The reaction stops being "过载" and becomes "星过载" for this hit.
        outcome.labelPrefix = LABEL_PREFIX

        var detail = ""
        when (key) {
            "vanish" -> {
                val requested = star * VEIL_SHIELD_SHARE
                val landed = grantAbsorption(
                    player,
                    absorptionHeadroomId(VEIL_SOURCE),
                    requested,
                    player.absorptionAmount + requested,
                )
                detail = ", shield=${"%.2f".format(landed)}"
            }
            "overload" -> detail = ", detonated=${detonate(player, target, star)}"
            "eclipse" -> {
                if (stolen != null) {
                    steal(player, target, stolen)
                    detail = ", stolen=${stolen.effectType.value().name.string}"
                }
            }
            "freeze" -> {
                HealLock.lock(target, SEAL_TICKS)
                detail = ", healLock=$SEAL_TICKS"
            }
            "brittle" -> {
                FRACTURE[target] = player.world.time + FRACTURE_TICKS
                spawn(target, ParticleTypes.CRIT, 10, 0.35, 0.1)
            }
        }

        var splashed = 0
        val splash = star * SPLASH_SHARE
        for (other in player.world.getOtherEntities(player, target.boundingBox.expand(SPLASH_RADIUS)) { it is LivingEntity }) {
            val living = other as LivingEntity
            if (living == target || living == player || !living.isAlive) continue
            living.damageElemental(player, ModDamageTypes.STAR, splash)
            splashed++
        }

        FSTLog.log("StarBlessing") {
            "element=${element.id}, lvl=$level, reaction=${"%.2f".format(flatDamage)}, " +
                "star=${"%.2f".format(star)}, splashed=$splashed, effect=$key$detail, outcome=star-conversion"
        }
    }

    /** `overload`: 星爆 — everything around the target takes a share and gets shoved away from it. */
    private fun detonate(player: PlayerEntity, target: LivingEntity, star: Float): Int {
        val world = player.world
        var hits = 0
        for (other in world.getOtherEntities(target, target.boundingBox.expand(DETONATE_RADIUS)) { it is LivingEntity && it.isAlive }) {
            val living = other as LivingEntity
            if (living === player) continue
            living.damageElemental(player, ModDamageTypes.STAR, star * DETONATE_SHARE)
            living.takeKnockback(DETONATE_KNOCKBACK, target.x - living.x, target.z - living.z)
            hits++
        }
        (world as? ServerWorld)?.spawnParticles(
            ParticleTypes.EXPLOSION,
            target.x, target.y + target.height * 0.5, target.z,
            1, 0.0, 0.0, 0.0, 0.0,
        )
        return hits
    }

    /** `eclipse`: 星蚀 — the buff changes hands, at half duration. */
    private fun steal(player: PlayerEntity, target: LivingEntity, effect: StatusEffectInstance) {
        target.removeStatusEffect(effect.effectType)
        player.addStatusEffect(
            StatusEffectInstance(effect.effectType, (effect.duration / 2).coerceAtLeast(20), effect.amplifier)
        )
    }

    private fun firstBeneficialEffect(target: LivingEntity): StatusEffectInstance? =
        target.statusEffects.firstOrNull { it.effectType.value().isBeneficial && it.duration > 0 }

    /** `brittle`: 星裂 — real hits land harder while the fracture holds. */
    @SubscribeEvent
    fun onHurt(e: LivingHurtEvent) {
        if (e.source.isAuxiliaryDamage()) return
        val target = e.entityLiving
        val until = FRACTURE[target] ?: return
        if (target.world.time >= until) {
            FRACTURE.remove(target)
            return
        }
        val before = e.amount
        e.amount *= 1f + FRACTURE_BONUS
        FSTLog.log("StarBlessing") {
            "target=${target.name.string}, amount=${"%.2f".format(before)}->${"%.2f".format(e.amount)}, " +
                "outcome=fracture-amplified"
        }
    }

    private fun spawn(target: LivingEntity, particle: ParticleEffect, count: Int, spread: Double, speed: Double) {
        val world = target.world as? ServerWorld ?: return
        world.spawnParticles(particle, target.x, target.y + target.height * 0.5, target.z, count, spread, spread, spread, speed)
    }
}
