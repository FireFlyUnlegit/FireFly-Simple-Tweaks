package dev.firefly.simpletweaks.util.elements

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.util.damagesource.damageElemental
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.attribute.EntityAttributeInstance
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import java.util.UUID
import net.minecraft.util.Identifier
import java.util.WeakHashMap

/**
 * Per-entity element marks and the reaction engine.
 *
 * ## State discipline (this is the part that has burned this repo before)
 * Marks live in a [WeakHashMap] keyed by the entity **object**, not in any data component and not in
 * the entity's NBT. Consequences, all deliberate:
 *
 * - **Nothing is persisted.** A mark cannot survive a relog, a chunk unload or a server restart, so
 *   there is no stale-mark bug of the `heavenly_punishment` kind ("the duration is gone but the
 *   modifier is still in the save").
 * - **Nothing needs syncing.** A mark is invisible to the player (it only shows as the reaction
 *   trigger) and is only ever read on the server thread, in the same event that writes it, so there is
 *   no client half to keep in step — see `AGENTS.md` §4.11 for the failures that come from skipping
 *   that question.
 * - **[WeakHashMap] entries vanish with the entity.** A dead-and-removed zombie cannot leave a mark
 *   behind pointing at a new entity, because the key IS the object.
 *
 * ## Timing
 * A mark lasts [MARK_TICKS] ticks of *real time*, checked lazily on the next hit (there is no tick
 * handler): if the next hit arrives later than that, the mark is treated as absent. A lazy check costs
 * nothing per tick and cannot drift, because the only consumer is "the next hit".
 *
 * ## Why the reaction damage is dealt by [ElementCombat.onLivingHurt]'s caller
 * The engine returns the reaction that fired instead of applying it, so the one place that already
 * owns "deal element damage, credit the attacker, log one line" stays the only place that does it.
 */
object ElementMarks {

    /** How long a mark survives without a follow-up hit. 200 ticks = 10 s. */
    const val MARK_TICKS = 200

    /** One mark: which element, and the tick it expires at. */
    private class Mark(val element: ElementType, val expireAt: Long)

    private val marks = WeakHashMap<LivingEntity, Mark>()

    /**
     * Records [element] as the target's mark, **after** checking for a reaction against the mark that
     * was already there.
     *
     * @return the reaction that fired and was consumed, or `null`
     */
    fun recordAndReact(
        attacker: PlayerEntity,
        target: LivingEntity,
        element: ElementType,
        damage: Float,
    ): String? {
        val now = target.world.time
        val previous = marks[target]

        var reactionName: String? = null
        if (previous != null && previous.expireAt > now) {
            val reaction = ElementReactions.of(previous.element, element)
            if (reaction != null) {
                apply(attacker, target, reaction, damage)
                reactionName = reaction.key
            }
        }

        // A reaction consumes the old mark; either way the new hit leaves its own.
        marks[target] = Mark(element, now + MARK_TICKS)
        return reactionName
    }

    /** Records [element] without checking for a reaction (used by non-converted hits). */
    fun record(attacker: PlayerEntity, target: LivingEntity, element: ElementType, damage: Float) {
        recordAndReact(attacker, target, element, damage)
    }

    /**
     * Applies one fired reaction.
     *
     * Every field of [ElementReaction] is optional, and each is applied only when non-zero — that is
     * what lets "bloom" be a pure crowd-control reaction with no damage at all.
     */
    private fun apply(attacker: PlayerEntity, target: LivingEntity, reaction: ElementReaction, damage: Float) {
        // Percentage of the triggering hit, dealt as this reaction's trigger element so resistances
        // see it. Scaled by `damage` on purpose: a flat bonus would decide a wooden-sword fight and be
        // noise against a Sharpness V netherite sword.
        //
        // `damage` already carries the attacker's mastery multiplier (ElementCombat passes
        // `original * mastery`), so a reaction inherits mastery exactly once — through the hit that
        // triggered it — and does NOT apply the multiplier a second time here.
        //
        // ⚠️ The bonus is **queued**, not dealt here. This runs inside `LivingHurtEvent` (from
        // `ElementCombat.onLivingHurt`), and any `LivingEntity#damage` call made from inside that event
        // re-enters `EventSeams.hurt`, overwrites its ThreadLocal cache and makes the outer
        // `@ModifyVariable` re-fire a fresh event — discarding the conversion's own `e.amount` change.
        // That is what made a 20 HP mob take 4 hits while the log claimed both halves were dealt.
        // Status effects and attribute changes are not damage calls, so they stay here (they must
        // happen before the target can act).
        // `elemental_resonance` multiplies **reaction** damage only. Mastery is not applied again here
        // (the caller already folded it into `damage`); this is a separate axis on purpose, so a
        // reaction build differs from a raw-element build.
        val resonance = ElementCombat.resonanceMultiplier(attacker)
        val bonus = damage * reaction.damageBonus * resonance
        if (bonus > 0f && target.isAlive) {
            ElementCombat.enqueueElemental(attacker, target, reaction.trigger, bonus)
        }

        // On-screen confirmation of the reaction. Author: "触发某种元素反应能不能生成一些文字来提示一下?"
        //
        // Sent as an **action bar** message (`sendMessage(text, true)`) to the attacker and to every
        // player within 16 blocks, because a reaction is a group-visible event and the vanilla action
        // bar is the one HUD slot that costs no extra packet or screen space. Throttled per player so a
        // rapid combo cannot turn the bar into a flipbook.
        noticeReaction(attacker, target, reaction, bonus)

        if (reaction.armorShredFraction > 0f && reaction.duration > 0) {
            armourShred(target, reaction.armorShredFraction, reaction.duration)
        }

        if (reaction.freezeTicks > 0) {
            // Vanilla powder-snow counter. Clamped by the game itself; setting it is the documented way.
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

        if (reaction.healAttacker > 0f && damage > 0f && attacker.isAlive) {
            attacker.heal(damage * reaction.healAttacker)
        }

        spawnBurst(target, reaction.trigger)

        FSTLog.log("Element") {
            "reaction=${reaction.key}, mark=${reaction.mark.id}, trigger=${reaction.trigger.id}, " +
                "target=${target.type.name.string}, damage=${"%.2f".format(damage)}, " +
                "bonusPct=${"%.0f".format(reaction.damageBonus * 100)}%, " +
                "bonus=${"%.2f".format(bonus)}, " +
                "shredPct=${"%.0f".format(reaction.armorShredFraction * 100)}%, " +
                "heal=${"%.2f".format(damage * reaction.healAttacker)}, " +
                "outcome=reacted"
        }
    }

    /**
     * Temporary `generic.armor` reduction on [target], by **[fraction] of its current armour**.
     *
     * Percentage rather than a flat number because armour is a scale: `-4` is 40% of an iron chestplate
     * and 10% of a netherite set. A fraction keeps the shred feeling identical at every tier.
     *
     * Uses **temporary** modifiers (`addTemporaryModifier`), never persistent ones: the reduction is
     * released by a timer in memory, and a persistent modifier whose timer died with the process is
     * exactly the "permanent residue" bug `AGENTS.md` §4.2 records from `heavenly_punishment`.
     *
     * The id is **fixed** (`element_shred`) on purpose, and the previous shred is removed first: the
     * modifier list is additive, so a per-source id would let two reactions stack into a larger shred
     * than either was tuned for. A new reaction *replaces* the running one. `expireShreds` removes it
     * by the same id.
     */
    private fun armourShred(target: LivingEntity, fraction: Float, duration: Int) {
        val instance: EntityAttributeInstance =
            target.getAttributeInstance(EntityAttributes.GENERIC_ARMOR) ?: return

        val id = Identifier.of("simple_tweaks", "element_shred")
        // Strip the old one *before* reading, so a second reaction shreds the target's real armour
        // rather than a value that already includes the previous shred.
        instance.removeModifier(id)

        // Read the armour this target actually has (base + armour modifiers + effects), which is what
        // the damage formula will use.
        val current = instance.value
        if (current <= 0.0) return
        val amount = (current * fraction).coerceAtLeast(0.0)
        if (amount <= 0.0) return

        instance.addTemporaryModifier(
            EntityAttributeModifier(
                id,
                -amount,
                EntityAttributeModifier.Operation.ADD_VALUE,
            )
        )
        SHREDS[target] = ShredState(expireAt = target.world.time + duration)

        // `AGENTS.md` §4.9 rule 2: log the **landed** value, not the requested one. `getValue()` can be
        // clamped (armour is clamped at 0 by its attribute's own `clamp`), so "I asked for -12" and
        // "armour actually dropped by 12" are different facts and only the second one is observable.
        val landed = instance.value
        val requestedDrop = amount
        val actualDrop = current - landed

        FSTLog.log("Element") {
            "outcome=armor-shred, target=${target.type.name.string}, " +
                "armor=${"%.2f".format(current)}->${"%.2f".format(landed)}, " +
                "requested=${"%.2f".format(requestedDrop)}, landed=${"%.2f".format(actualDrop)}, " +
                (if (actualDrop < requestedDrop - 0.01) "clamped=true, " else "") +
                "ticks=$duration"
        }
    }

    /** A pending armour shred: when to remove it. */
    private class ShredState(val expireAt: Long)

    private val SHREDS = WeakHashMap<LivingEntity, ShredState>()

    /**
     * Removes every expired armour shred. Called from the per-entity tick seam
     * (`EntityTickMixin` → `LivingEvent.LivingUpdateEvent`) by `ElementShredTicker`.
     */
    fun expireShreds(entity: LivingEntity) {
        val state = SHREDS[entity] ?: return
        if (entity.world.time < state.expireAt) return
        SHREDS.remove(entity)
        val instance = entity.getAttributeInstance(EntityAttributes.GENERIC_ARMOR) ?: return
        instance.removeModifier(Identifier.of("simple_tweaks", "element_shred"))
    }

    /** A small particle burst in the trigger element's colour — purely client-visible feedback. */
    private fun spawnBurst(target: LivingEntity, element: ElementType) {
        val world = target.world as? ServerWorld ?: return
        world.spawnParticles(
            element.particle(),
            target.x, target.getBodyY(0.5), target.z,
            12,
            0.4, 0.4, 0.4,
            0.05,
        )
    }

    /**
     * Shows `「感电」 +28.3` in the action bar when a reaction fires.
     *
     * Receivers are the attacker plus everyone within 16 blocks (a reaction is a shared, visible
     * event), and the text is throttled **per player** so two players triggering at once cannot starve
     * each other. Uses the vanilla action bar, so nothing extra is registered or synced: `sendMessage`
     * with `overlay = true` is an ordinary `GameMessageS2CPacket`.
     */
    private val lastNotice = java.util.concurrent.ConcurrentHashMap<UUID, Long>()

    private fun noticeReaction(
        attacker: PlayerEntity,
        target: LivingEntity,
        reaction: ElementReaction,
        bonus: Float,
    ) {
        val world = target.world as? ServerWorld ?: return
        val now = world.time

        val name = Text.translatable("reaction.simple_tweaks.${reaction.key}")
            .formatted(Formatting.BOLD)
        val nameColoured = name.copy().setStyle(
            name.style.withColor(net.minecraft.text.TextColor.fromRgb(reaction.trigger.color and 0xFFFFFF)),
        )
        val line: Text = Text.empty()
            .append(nameColoured)
            .append(Text.literal(if (bonus > 0f) "  ${"%.1f".format(bonus)}" else "").formatted(Formatting.GRAY))

        for (player in world.players) {
            if (player.squaredDistanceTo(target) > 16.0 * 16.0) continue
            // ⚠️ The `last != null` test is load-bearing. The first version defaulted the missing entry
            // to `Long.MIN_VALUE`, and `now - Long.MIN_VALUE` **overflows** into a negative number, so
            // `now - last < cooldown` was true for every player forever and **every** notice was
            // swallowed — the reported "触发反应没有文字提示".
            val last = lastNotice[player.uuid]
            if (last != null && now - last < NOTICE_COOLDOWN_TICKS) continue
            lastNotice[player.uuid] = now
            player.sendMessage(line, true)
        }
    }

    /** Minimum gap between two reaction notices for the same player, in ticks. */
    private const val NOTICE_COOLDOWN_TICKS = 10L

    /** For diagnostics and the checklist: does [entity] currently carry a live mark? */
    fun markOf(entity: LivingEntity): ElementType? {
        val mark = marks[entity] ?: return null
        return if (mark.expireAt > entity.world.time) mark.element else null
    }
}

/**
 * The particle each element's reaction shows. Vanilla particle types only — this mod registers one
 * custom particle already (`celestial_ring`) and adding eight more for feedback is not worth it.
 */
fun ElementType.particle(): net.minecraft.particle.ParticleEffect = when (this) {
    ElementType.PHYSICAL -> ParticleTypes.CRIT
    ElementType.FIRE -> ParticleTypes.FLAME
    ElementType.FROST -> ParticleTypes.SNOWFLAKE
    ElementType.LIGHTNING -> ParticleTypes.ELECTRIC_SPARK
    ElementType.WATER -> ParticleTypes.SPLASH
    ElementType.NATURE -> ParticleTypes.HAPPY_VILLAGER
    ElementType.DARK -> ParticleTypes.SOUL
    ElementType.LIGHT -> ParticleTypes.END_ROD
}

/**
 * Releases expired armour shreds from the per-entity tick seam.
 *
 * A `LivingEntity` has no "run this after N ticks" API and the modifier is on an *attribute*, so the
 * only way to undo it without a scheduled task is to notice on a tick. This object is registered from
 * `SimpleTweaks.onInitialize` **before** the enchantment handlers, and it does almost nothing: one map
 * lookup per entity per tick, and only entities that were actually shredded are ever in that map.
 *
 * ⚠️ Do **not** turn this into an unconditional `removeModifier` every tick — that would allocate a
 * new `Identifier` per entity per tick (thousands of allocations per second) and would also delete a
 * player's *other* armour modifiers that happen to share the id prefix. Both mistakes have cost this
 * repo before in other handlers.
 */
object ElementShredTicker : dev.firefly.simpletweaks.core.Listenable {

    @dev.firefly.simpletweaks.compat.event.SubscribeEvent
    fun onLivingUpdate(e: dev.firefly.simpletweaks.compat.event.LivingEvent.LivingUpdateEvent) {
        ElementMarks.expireShreds(e.entityLiving)
    }
}
