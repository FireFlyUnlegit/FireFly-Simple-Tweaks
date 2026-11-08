package dev.firefly.simpletweaks.util.elements

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.util.damagesource.damageElemental
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.chestplate
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.attribute.EntityAttributeInstance
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import java.util.UUID
import java.util.WeakHashMap

/**
 * What one hit did to the aura / reaction system.
 *
 * Returned rather than applied, because the caller ([ElementCombat]) is the only place allowed to
 * touch `e.amount` and the only place that owns "deal element damage, credit the attacker, log one
 * line" — see the note on re-entrancy in [apply].
 */
data class ReactionOutcome(
    /** Reaction key that fired, or `null`. */
    val key: String? = null,
    /** Multiplier the caller must apply to the triggering hit (1.0 = untouched). 增幅 reactions only. */
    val amplify: Float = 1f,
    /** Flat damage already queued as element damage by this call (for the log line). */
    val flatDamage: Float = 0f,
)

/**
 * Per-entity elemental auras and the reaction engine.
 *
 * ## State discipline (the part that has burned this repo before)
 * Auras live in [ElementAuras], keyed by the entity **object**: nothing is persisted, nothing is synced,
 * and an aura can never outlive the entity. Everything is read lazily by "the next hit" — there is no
 * tick handler for auras, so an aura costs nothing per tick and cannot drift.
 *
 * ## Attachment ICD
 * A hit only attaches an aura if [AttachmentIcd] allows it (2.5 s window + every 3rd hit). A hit that is
 * throttled therefore **cannot** react either — it never applied its element. A few sources bypass the
 * ICD on purpose (`forceAttach`): the poison tick (a DoT has no "attack class"), and anything the mod
 * itself re-deals, which was already funded by the hit that created it.
 *
 * ## Reaction resolution
 * 1. read the aura; if it is empty or the throttled hit cannot attach, nothing happens (the hit's own
 *    damage is untouched);
 * 2. if `(aura, trigger)` is in [ElementReactions], consume `triggerUnits × dampening` aura units, then
 *    apply the reaction (amplify / flat damage / quicken / status effects);
 * 3. leftover aura stays (先手不残留是被吃掉的部分), the trigger never lingers (后手不残留), so no
 *    reaction can feed itself;
 * 4. otherwise attach the trigger's aura.
 */
object ElementMarks {

    /** Kept for the two call sites that still describe the lifetime in a log line. */
    const val MARK_TICKS = ElementAuras.WEAK_TICKS

    // ------------------------------------------------------------------ public API

    /**
     * Applies [element] to [target] and resolves a reaction against whatever aura was already there.
     *
     * @param units aura units this hit carries ([ElementAuras.WEAK] / [STRONG] / [ULTRA])
     * @param damage the triggering hit's damage, after mastery; only amplifying reactions look at it
     * @param forceAttach bypass the attachment ICD (DoT ticks and the mod's own re-dealt damage)
     */
    fun recordAndReact(
        attacker: PlayerEntity?,
        target: LivingEntity,
        element: ElementType,
        damage: Float,
        units: Float = ElementAuras.WEAK,
        forceAttach: Boolean = false,
        /**
         * The hit that triggered this, when the caller has it.
         *
         * Threaded through so the reaction's own scaling reads the **weapon that dealt the hit** rather
         * than whatever the attacker holds now — an arrow fired with a bow must not borrow the stats of
         * the sword the player swapped to while it flew (`build=cleanup31`).
         */
        source: net.minecraft.entity.damage.DamageSource? = null,
    ): ReactionOutcome {
        if (!target.isAlive) return ReactionOutcome()
        val now = target.world.time

        // 激化 state first: it is a buff on the target, not an aura, and it applies to nature/lightning
        // hits whether or not this particular hit was allowed to attach.
        var flat = 0f
        val quickened = QUICKENED[target]
        if (quickened != null && quickened <= now) QUICKENED.remove(target)
        val isQuickened = QUICKENED[target]?.let { it > now } == true

        // `elemental_catalyst` shortens the attachment window: 2.5 s -> 2.0 / 1.5 / 1.0 s at I / II / III.
        // It is applied here rather than inside `AttachmentIcd` because the counter is engine state and the
        // window is a per-attacker build choice; mixing them would make the ICD impossible to reason about.
        val icdWindow = ElementCombat.hasteWindow(
            (AttachmentIcd.WINDOW_TICKS - 10L * catalystLevel(attacker)).coerceAtLeast(20L),
            attacker,
        )
        if (!forceAttach && !AttachmentIcd.allows(target, attacker, icdWindow)) {
            FSTLog.log("Element") {
                "outcome=no-aura-throttled, element=${element.id}, aura=${ElementAuras.describe(target)}"
            }
            return ReactionOutcome()
        }

        val aura = ElementAuras.peek(target)
        var outcome = ReactionOutcome()

        if (aura != null && aura.first != element) {
            val (auraElement, auraUnits) = aura
            val reaction = ElementReactions.of(auraElement, element)
            if (reaction != null) {
                val dampening = ElementReactions.dampening(auraElement, element)
                // The table's `triggerUnits` is authoritative; the caller's units are a floor. A weak
                // trigger (an arrow, a mob attack) must still eat the strong aura a `melt` row declares,
                // otherwise the table reads as documentation that nothing honours (`build=cleanup63`).
                val cost = maxOf(units, reaction.triggerUnits) * dampening
                val consumed = ElementAuras.consume(target, cost)

                outcome = apply(attacker, target, reaction, damage, auraUnits, consumed, dampening, source)

                // `elemental_sovereign`: every reaction this attacker triggers is worth more. Applied to the
                // outcome rather than inside `apply` so amplifying and transformative reactions scale through
                // one number.
                val sovereign = sovereignLevel(attacker)
                if (sovereign > 0) {
                    val bonus = 1f + 0.30f * sovereign
                    outcome = ReactionOutcome(
                        key = outcome.key,
                        amplify = outcome.amplify * bonus,
                        flatDamage = outcome.flatDamage * bonus,
                    )
                }

                // Announce the reaction to whoever wants to build on it (`elemental_spread` attaches the same
                // element to nearby enemies). Fired **after** `apply`, so the reaction itself is already
                // resolved and a listener cannot change its numbers.
                // `elemental_supremacy`: remember what hit the wearer, so their next swing can replay it.
                ReactionMemory.recordIfWearing(target, reaction.key)

                ReactionSeam.fire(attacker, target, element, outcome)

                FSTLog.log("Element") {
                    "reaction=${reaction.key}, aura=${auraElement.id}:${"%.2f".format(auraUnits)}, " +
                        "trigger=${element.id}:${"%.2f".format(units)}, dampening=${"%.2f".format(dampening)}, " +
                        "consumed=${"%.2f".format(consumed)}, left=${ElementAuras.describe(target)}, " +
                        "amplify=${"%.2f".format(outcome.amplify)}, flat=${"%.2f".format(outcome.flatDamage)}, " +
                        "outcome=reacted"
                }
                // 后手不残留: the trigger element does not stay behind after a reaction.
                return outcome
            }
        }

        // No reaction: this hit becomes the new aura (same element refreshes, different replaces).
        ElementAuras.attach(target, element, units)
        if (isQuickened && (element == ElementType.NATURE || element == ElementType.LIGHTNING)) {
            flat = quickenBonus(attacker, target, reactionKey = null)
            if (flat > 0f && attacker != null) {
                ElementCombat.enqueueElemental(attacker, target, element, flat)
            }
            outcome = ReactionOutcome(key = null, flatDamage = flat)
        }
        return outcome
    }

    /** Convenience wrapper for the two call sites that only want the reaction name. */
    fun record(
        attacker: PlayerEntity?,
        target: LivingEntity,
        element: ElementType,
        damage: Float,
        units: Float = ElementAuras.WEAK,
        source: net.minecraft.entity.damage.DamageSource? = null,
    ): ReactionOutcome = recordAndReact(attacker, target, element, damage, units, source = source)

    /**
     * Sets [element] as the target's aura **without** looking for a reaction.
     *
     * For damage that repeats on a timer — today only the poison tick. A reaction per tick made one
     * poisoned target add 50 % of the tick damage every second, i.e. the same "伤害爆高" the on-hit
     * guard fixes from the enchantment side; the aura itself is still refreshed so a poisoned target hit
     * by fire triggers `burn` from the player's own swing, once.
     */
    fun markOnly(target: LivingEntity, element: ElementType, units: Float = ElementAuras.WEAK) {
        if (!target.isAlive) return
        ElementAuras.attach(target, element, units)
    }

    // ------------------------------------------------------------------ reaction application

    /**
     * Applies one fired reaction.
     *
     * ⚠️ **Nothing here calls `LivingEntity#damage`.** This runs inside `LivingHurtEvent`, and a nested
     * damage call re-enters `EventSeams.hurt`, overwrites its ThreadLocal cache and makes the outer
     * `@ModifyVariable` re-fire a fresh event — which silently discards every mutation this handler made
     * to `e.amount`. The measured symptom was a 20 HP mob taking four hits while the log claimed both
     * shares landed. Damage is therefore **queued** ([ElementCombat.enqueueElemental]) and flushed from
     * the world tick. Status effects and attribute writes are not damage calls and stay inline.
     */
    private fun apply(
        attacker: PlayerEntity?,
        target: LivingEntity,
        reaction: ElementReaction,
        damage: Float,
        auraUnits: Float,
        consumed: Float,
        dampening: Float,
        source: net.minecraft.entity.damage.DamageSource? = null,
    ): ReactionOutcome {
        val resonance = attacker?.let { ElementCombat.resonanceMultiplier(it, source) } ?: 1f

        // 增幅: hand the multiplier back to the caller, which owns `e.amount`.
        val amplify = reaction.amplify

        // 剧变: its own flat hit, scaled by attacker level + mastery (never by the weapon).
        //
        // ⚠️ **Per-target ICD.** The reference gives the big transformative reactions their own internal
        // cooldown (感电 ≈ 1 s on the same target), and without one a fast attacker re-triggered the same
        // reaction every swing — the "reaction spam" the ICD exists to prevent. The status effects below
        // still apply; only the damage is throttled, so a reaction is never "free" but also never a
        // second damage source at melee speed.
        var flat = 0f
        if (reaction.transformativeCoeff > 0f) {
            val now = target.world.time
            val last = TRANSFORMATIVE_AT[target]
            // `elemental_supremacy` at full investment collapses this window to a single tick.
        if (last != null && now - last < ElementCombat.hasteWindow(TRANSFORMATIVE_ICD_TICKS, attacker)) {
                FSTLog.log("Element") {
                    "reaction=${reaction.key}, target=${target.type.name.string}, " +
                        "sinceLast=${now - last}, outcome=transformative-icd"
                }
            } else {
                TRANSFORMATIVE_AT[target] = now
                flat = transformativeDamage(attacker, reaction.transformativeCoeff) * resonance
                if (flat > 0f && attacker != null) {
                    ElementCombat.enqueueElemental(attacker, target, reaction.trigger, flat)
                }
            }
        }

        // 激化: no damage now; it puts the target into the quickened state.
        if (reaction.quicken) {
            QUICKENED[target] = target.world.time + reaction.quickenTicks
            FSTLog.log("Element") {
                "reaction=quicken, target=${target.type.name.string}, ticks=${reaction.quickenTicks}, " +
                    "outcome=quickened"
            }
        }

        // The reaction's own on-screen line (floating label above the target).
        val labelled = if (amplify > 1f) {
            // 增幅 shows the damage the multiplier added, so the label stays in the same units as
            // every other reaction line: `融化 (-12.50)` = "this reaction added 12.5".
            damage * (amplify - 1f)
        } else {
            flat
        }
        noticeReaction(attacker, target, reaction, labelled)

        if (reaction.armorShredFraction > 0f && reaction.duration > 0) {
            armourShred(target, reaction.armorShredFraction, reaction.duration)
        }
        if (reaction.freezeTicks > 0) {
            target.frozenTicks = target.frozenTicks.coerceAtLeast(reaction.freezeTicks)
        }
        if (reaction.slowTicks > 0) {
            target.addStatusEffect(
                StatusEffectInstance(StatusEffects.SLOWNESS, reaction.slowTicks, reaction.slownessAmplifier),
                attacker,
            )
        }
        if (reaction.blindTicks > 0) {
            target.addStatusEffect(StatusEffectInstance(StatusEffects.BLINDNESS, reaction.blindTicks), attacker)
        }
        if (reaction.healAttacker > 0f && damage > 0f && attacker != null && attacker.isAlive) {
            attacker.heal(damage * reaction.healAttacker)
        }

        spawnBurst(target, reaction.trigger)

        FSTLog.log("Element") {
            "reaction=${reaction.key}, mark=${reaction.mark.id}, trigger=${reaction.trigger.id}, " +
                "target=${target.type.name.string}, hitDamage=${"%.2f".format(damage)}, " +
                "auraUnits=${"%.2f".format(auraUnits)}, consumed=${"%.2f".format(consumed)}, " +
                "dampening=${"%.2f".format(dampening)}, amplify=${"%.2f".format(amplify)}, " +
                "flat=${"%.2f".format(flat)}, resonance=${"%.2f".format(resonance)}, " +
                "outcome=${if (amplify > 1f) "amplified" else "transformative"}"
        }
        return ReactionOutcome(reaction.key, amplify.coerceAtLeast(1f), flat)
    }

    /**
     * 剧变 reaction damage: `base + level × perLevel`, times the reaction's coefficient and the attacker's
     * mastery.
     *
     * Deliberately **independent of the weapon**: a transformative reaction is a floor that a weak
     * weapon can lean on and a strong weapon cannot multiply. Mastery counts here at a lower rate than
     * on elemental damage (`ElementCombat.MASTERY_PER_LEVEL` is 10 %; this uses 8 %), so the two enchant
     * builds stay distinct.
     *
     * The driver is **gear power**, not the experience level — see [REACTION_POWER_SCALE] for why the
     * XP-based version was replaced (`build=cleanup76`).
     */
    /**
     * `elemental_catalyst` level from [attacker]'s chestplate — the build choice that shortens the
     * attachment window.
     *
     * Read straight from the equipment rather than through a handler: it is a pure function of what the
     * attacker wears, and the ICD is consulted in the middle of damage resolution where the main-hand
     * window is already open (`ShotWeapon`), so no `DamageSource` plumbing is needed.
     */
    /**
     * `elemental_sovereign` level — scales **both** reaction outcomes by `1 + 0.30 × level`.
     *
     * Read here rather than in a handler for the same reason as `catalystLevel`: the reaction is resolved
     * inside this object, and a second owner of "how strong is a reaction" would let the engine and the
     * enchantment disagree.
     */
    private fun sovereignLevel(attacker: PlayerEntity?): Int =
        if (attacker == null) 0
        else getItemSpecificEnchantLevel(attacker.mainHandStack, GeneratedEnchantments.ELEMENTAL_SOVEREIGN)

    private fun catalystLevel(attacker: PlayerEntity?): Int =
        if (attacker == null) 0
        else getItemSpecificEnchantLevel(attacker.chestplate, GeneratedEnchantments.ELEMENTAL_CATALYST)

    private fun transformativeDamage(attacker: PlayerEntity?, coeff: Float): Float {
        val power = reactionPower(attacker)
        val base = TRANSFORMATIVE_BASE + power * TRANSFORMATIVE_PER_POWER
        val mastery = attacker?.let { ElementCombat.masteryLevel(it.mainHandStack) } ?: 0
        return base * coeff * (1f + TRANSFORMATIVE_MASTERY_PER_LEVEL * mastery)
    }

    /** Flat damage a nature/lightning hit gains while the target is 激化 (quicken) state. */
    private fun quickenBonus(attacker: PlayerEntity?, target: LivingEntity, reactionKey: String?): Float {
        val power = reactionPower(attacker)
        val base = QUICKEN_BASE + power * QUICKEN_PER_POWER
        val mastery = attacker?.let { ElementCombat.masteryLevel(it.mainHandStack) } ?: 0
        FSTLog.log("Element") {
            "reaction=quicken-bonus, target=${target.type.name.string}, power=${"%.2f".format(power)}, " +
                "base=${"%.2f".format(base)}, trigger=${reactionKey ?: "none"}, outcome=quickened-hit"
        }
        return base * (1f + TRANSFORMATIVE_MASTERY_PER_LEVEL * mastery)
    }

    // ------------------------------------------------------------------ constants

    /** Base of a transformative reaction at zero reaction power. */
    const val TRANSFORMATIVE_BASE = 4.0f

    /** Added per point of **reaction power** (see [reactionPower]). */
    const val TRANSFORMATIVE_PER_POWER = 0.55f

    /**
     * Converts [dev.firefly.simpletweaks.util.PlayerPower] into "reaction power", the number the two
     * reaction formulas scale from.
     *
     * ## Why gear power and not the experience level
     * The driver used to be `player.experienceLevel`, which is a **farmable currency**: a mob farm takes
     * it from 0 to 100+ without the player fighting anything, so reaction damage tracked AFK time rather
     * than progress. It was also volatile in the wrong direction — dying drops the level to ~0, so the
     * same sword hit for wildly different amounts before and after a death.
     *
     * [dev.firefly.simpletweaks.util.PlayerPower] is the **same number the difficulty curve uses** for
     * mob scaling (armour / health / attack damage / enchantment levels), so reactions and mobs now track
     * one progression: upgrading gear raises both. Nothing about it can be farmed — you either wear the
     * gear or you do not.
     *
     * ## Why ×5 and not ×1
     * `PlayerPower` is normalised so a fully geared vanilla player is `≈1`; the old XP curve produced
     * `4 + 0.55 × 30 ≈ 20.5` for a finished build. `×5` maps the whole progression onto that band
     * (naked `0.1 → 0.5`, iron `1.3 → 6.5`, endgame `5.3 → 26.5`) so the change is a **rebase, not a
     * rebalance**: the numbers a mid-game player sees hardly move.
     *
     * ## Deliberately **no clamp**
     * Gear power is uncapped, exactly like the difficulty curve it comes from, and the author's call is to
     * leave it that way: a build that keeps growing should keep hitting harder. The runaway case the old
     * cap guarded against (an AFK farm) no longer exists, and the multiplicative exploit (`execute`'s
     * health-pool nuke) was fixed separately in `build=cleanup70`.
     */
    const val REACTION_POWER_SCALE = 5f

    /**
     * Reaction power for [attacker]: `PlayerPower × `[REACTION_POWER_SCALE], or `0` without an attacker.
     *
     * Split out as a pure function of the gear-power value so it is testable without a `PlayerEntity`
     * (a game test has no player), which matters because this scaling must not silently regress.
     */
    fun reactionPowerFrom(gearPower: Double): Float = (gearPower * REACTION_POWER_SCALE).toFloat()

    private fun reactionPower(attacker: PlayerEntity?): Float =
        if (attacker == null) 0f
        else reactionPowerFrom(dev.firefly.simpletweaks.util.PlayerPower.of(attacker))

    /** Mastery contribution to transformative damage (lower than the 10 %/level elemental one). */
    const val TRANSFORMATIVE_MASTERY_PER_LEVEL = 0.08f

    /** 激化 (quicken) flat bonus per triggering hit. */
    const val QUICKEN_BASE = 2.5f
    const val QUICKEN_PER_POWER = 0.35f

    /**
     * Minimum gap between two **transformative** reaction hits on the same target, in ticks (1 s).
     *
     * See the note in [apply]: status effects are not throttled, damage is.
     */
    const val TRANSFORMATIVE_ICD_TICKS = 20L

    /** Target → tick of its last transformative reaction hit. */
    private val TRANSFORMATIVE_AT = WeakHashMap<LivingEntity, Long>()

    /** Target → tick at which its quickened state expires. */
    private val QUICKENED = WeakHashMap<LivingEntity, Long>()

    /** True while the target is in the 激化 state (used by the damage log and by `/fst`). */
    fun isQuickened(entity: LivingEntity): Boolean =
        QUICKENED[entity]?.let { it > entity.world.time } == true

    // ------------------------------------------------------------------ armour shred

    /**
     * Temporary `generic.armor` reduction on [target], by **[fraction] of its current armour**.
     *
     * Percentage rather than a flat number because armour is a scale. Uses **temporary** modifiers, never
     * persistent ones: the reduction is released by a timer in memory, and a persistent modifier whose
     * timer died with the process is exactly the "permanent residue" bug `AGENTS.md` §4.2 records.
     *
     * The id is **fixed** (`element_shred`) on purpose: the modifier list is additive, so a per-source id
     * would let two reactions stack into a larger shred than either was tuned for. A new reaction
     * *replaces* the running one.
     */
    private fun armourShred(target: LivingEntity, fraction: Float, duration: Int) {
        val instance: EntityAttributeInstance =
            target.getAttributeInstance(EntityAttributes.GENERIC_ARMOR) ?: return

        val id = Identifier.of("simple_tweaks", "element_shred")
        instance.removeModifier(id)

        val current = instance.value
        if (current <= 0.0) return
        val amount = (current * fraction).coerceAtLeast(0.0)
        if (amount <= 0.0) return

        instance.addTemporaryModifier(
            EntityAttributeModifier(id, -amount, EntityAttributeModifier.Operation.ADD_VALUE)
        )
        SHREDS[target] = ShredState(expireAt = target.world.time + duration)

        val landed = instance.value
        val actualDrop = current - landed
        FSTLog.log("Element") {
            "outcome=armor-shred, target=${target.type.name.string}, " +
                "armor=${"%.2f".format(current)}->${"%.2f".format(landed)}, " +
                "requested=${"%.2f".format(amount)}, landed=${"%.2f".format(actualDrop)}, " +
                (if (actualDrop < amount - 0.01) "clamped=true, " else "") + "ticks=$duration"
        }
    }

    /** A pending armour shred: when to remove it. */
    private class ShredState(val expireAt: Long)

    private val SHREDS = WeakHashMap<LivingEntity, ShredState>()

    /** Removes every expired armour shred (called from the per-entity tick seam). */
    fun expireShreds(entity: LivingEntity) {
        val state = SHREDS[entity] ?: return
        if (entity.world.time < state.expireAt) return
        SHREDS.remove(entity)
        val instance = entity.getAttributeInstance(EntityAttributes.GENERIC_ARMOR) ?: return
        instance.removeModifier(Identifier.of("simple_tweaks", "element_shred"))
    }

    // ------------------------------------------------------------------ feedback

    /** A small particle burst in the trigger element's colour — purely visual. */
    private fun spawnBurst(target: LivingEntity, element: ElementType) {
        val world = target.world as? ServerWorld ?: return
        world.spawnParticles(
            element.particle(),
            target.x, target.getBodyY(0.5), target.z,
            12, 0.4, 0.4, 0.4, 0.05,
        )
    }

    private val lastNotice = java.util.concurrent.ConcurrentHashMap<UUID, Long>()

    /**
     * Floating label above the target: `感电 (-28.30)` / `融化 (-12.50)`.
     *
     * Sent as a `ROLE_LABEL` damage-indicator packet (`build=cleanup18`), so it rises and fades with the
     * damage numbers of the same hit instead of appearing on the action bar at the bottom of the screen.
     * Throttled per target, because a label is spawned per hit rather than per player.
     */
    private fun noticeReaction(
        attacker: PlayerEntity?,
        target: LivingEntity,
        reaction: ElementReaction,
        amount: Float,
    ) {
        val world = target.world as? ServerWorld ?: return
        val now = world.time

        // ⚠️ The `last != null` test is load-bearing: defaulting to `Long.MIN_VALUE` made
        // `now - last` overflow negative, so every notice was swallowed.
        val last = lastNotice[target.uuid]
        if (last != null && now - last < NOTICE_COOLDOWN_TICKS) return
        lastNotice[target.uuid] = now

        dev.firefly.simpletweaks.damageindicator.DamageIndicatorHandler.broadcastLabel(
            target,
            "reaction.simple_tweaks.${reaction.key}",
            reaction.trigger.ordinal,
            amount,
        )
        FSTLog.log("Element") {
            "attacker=${attacker?.name?.string ?: "none"}, target=${target.type.name.string}, " +
                "reaction=${reaction.key}, shown=${"%.2f".format(amount)}, outcome=reaction-labelled"
        }
    }

    /** Minimum gap between two reaction labels on the same target, in ticks. */
    private const val NOTICE_COOLDOWN_TICKS = 10L

    /** Element of the live aura, or `null` (kept for diagnostics). */
    fun markOf(entity: LivingEntity): ElementType? = ElementAuras.elementOf(entity)
}

/** Particle for each element, used by the reaction burst. */
fun ElementType.particle(): net.minecraft.particle.ParticleEffect = when (this) {
    ElementType.PHYSICAL -> ParticleTypes.CRIT
    ElementType.FIRE -> ParticleTypes.FLAME
    ElementType.FROST -> ParticleTypes.SNOWFLAKE
    ElementType.LIGHTNING -> ParticleTypes.ELECTRIC_SPARK
    ElementType.WATER -> ParticleTypes.SPLASH
    ElementType.NATURE -> ParticleTypes.HAPPY_VILLAGER
    ElementType.DARK -> ParticleTypes.SQUID_INK
    ElementType.LIGHT -> ParticleTypes.END_ROD
}

/**
 * Releases expired armour shreds once per entity tick.
 *
 * A tiny separate listener rather than a world-tick sweep: the entity that carries the shred is exactly
 * the entity whose tick can release it, and the map is keyed by that entity.
 */
object ElementShredTicker : dev.firefly.simpletweaks.core.Listenable {

    @dev.firefly.simpletweaks.compat.event.SubscribeEvent
    fun onLivingUpdate(e: dev.firefly.simpletweaks.compat.event.LivingEvent.LivingUpdateEvent) {
        if (e.entityLiving.world.isClient) return
        ElementMarks.expireShreds(e.entityLiving)
    }
}
