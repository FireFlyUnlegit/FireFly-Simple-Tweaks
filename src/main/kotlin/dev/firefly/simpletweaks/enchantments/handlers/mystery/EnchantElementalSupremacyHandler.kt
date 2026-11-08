package dev.firefly.simpletweaks.enchantments.handlers.mystery

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.*
import dev.firefly.simpletweaks.util.combatStack
import dev.firefly.simpletweaks.util.elements.ElementAuras
import dev.firefly.simpletweaks.util.elements.ElementCombat
import dev.firefly.simpletweaks.util.elements.ElementMarks
import dev.firefly.simpletweaks.util.elements.ElementReactions
import dev.firefly.simpletweaks.util.elements.ElementType
import dev.firefly.simpletweaks.util.elements.ReactionMemory
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity

/**
 * 万法归元 — 「you return what you were given」.
 *
 * ## Passive half — one affinity, eight elements
 * `ElementCombat.protectionLevel` is the single reader of protective levels, and with this enchantment worn it
 * answers with the **highest** single-element total for **every** element (a maximum, never a sum, so it cannot
 * be stacked into immunity). Four pieces of fire protection defend exactly as well against frost.
 *
 * ## Active half — the reaction memory
 * Every elemental reaction that lands on the wearer is stored ([ReactionMemory], FIFO, cap 5). The wearer's
 * **next** hit replays all of them onto the target: for each remembered key the engine is asked to resolve the
 * same reaction, with the mark aura attached first so the replay has something to react with.
 *
 * This is what makes the enchantment MYSTERY rather than "a defensive stat": it turns everything the elemental
 * system throws at you into ammunition, and the ammunition's *kind* is decided by what actually hit you.
 *
 * ## Third half — free elemental riders
 * Each hit also carries `5 %` of its damage as a **converted elemental rider**, `floor(total level / 10)` times
 * (0 at 1–9 levels, 1 at 10–19, 2 at 20). These riders are *extra* damage — the original hit is not reduced —
 * and each one goes through `recordAndReact`, so it can start a reaction on whatever aura the target already
 * has. The element is picked at random from the eight, which is what makes it "some element" rather than a
 * fixed conversion.
 *
 * ## Why the replay goes through `ElementMarks` and not raw damage
 * Replaying the reaction through the engine means the replay gets the same amplification / transformative maths,
 * the same status effects (shred, freeze, blind) and the same logging as a normally triggered reaction. Rolling
 * our own numbers here would be a second, drifting implementation of the reaction table.
 *
 * ## Guards
 * - `isOwnElementalDamage` is skipped: the replayed reactions deal our own elemental damage, and feeding that
 *   back in would cascade;
 * - each replay attaches its mark with `forceAttach = true`, because the point is to *not* be gated by the
 *   attachment ICD the same way a normal hit is.
 */
@ModEnchantment(
    id = "elemental_supremacy",
    category = EnchantCategory.MYSTERY,
    type = EnchantType.ARMOR,
    maxLevel = 5,
    weight = EnchantItemWeights.MYSTERY,
    anvilCost = 90,
    minCostBase = 30,
    minCostPerLevel = 30,
    supportedItems = EnchantSupportedItemTags.ARMOR,
    slots = [EnchantSlot.ARMOR],
)
object EnchantElementalSupremacyHandler : Listenable {

    /** Share of the triggering hit fed into each replayed reaction. */
    private const val REPLAY_SHARE = 0.4f

    /** Aura units the replay attaches before triggering, so even an empty target reacts. */
    private const val REPLAY_AURA = ElementAuras.STRONG

    /** Share of the hit that each free elemental rider carries. */
    private const val RIDER_SHARE = 0.05f

    /** Total enchantment levels needed per extra rider (floor). */
    private const val RIDER_LEVELS_EACH = 10

    /**
     * Aura units the **counter** attaches onto an attacker, versus the 1.0 the active hit applies.
     *
     * Half of an active application on purpose: the counter is free (it costs the wearer nothing but
     * being hit), so it must not out-value the attack the player chose to make. At `0.5` two counters
     * equal one active attachment, which is exactly the "setting up next hit" role the author described.
     */
    private const val COUNTER_UNITS = 0.5f

    /**
     * 反伤/反击 — being hit by an element answers with the element that **counters** it, attached onto
     * the attacker.
     *
     * ## Why this direction is separate from the attack branch
     * The attack branch fires when the wearer is the *attacker*; this one fires when the wearer is the
     * *victim*. They are two halves of one identity ("elemental superiority cuts both ways") and both need
     * `wearsSupremacy` on their own side, so they are two listeners on the same event rather than one
     * listener with a mode flag — each has its own guards and neither can accidentally run the other's
     * body.
     *
     * ## The two deliberate exclusions
     * 1. **The other side's 万法** (`wearsSupremacy(attacker)`) — otherwise two wearers trading hits would
     *    keep answering each other's answers, and the winner would be whoever's aura happened to survive
     *    the exchange. Author's rule: the counter is for *normal* elemental attacks.
     * 2. **Physical damage** — `counterOf` returns `null`, so a sword swing does nothing; 万法's counter is
     *    about the element system.
     *
     * ## Why it attaches instead of dealing damage
     * "反伤" here is a **setup**, not a second damage source: the attack already went through the normal
     * pipeline (the armour's 4.5 %/level mitigation included), and a damage rider on top would be a
     * stealth DPS increase nobody asked for. Attaching 0.5 units means the attacker now carries an aura
     * that *their own* element is weak to — the wearer's next hit reacts with it.
     *
     * No `wouldBeDiscardedByIFrames` gate: attaching an aura deals no damage, so a gated swing cannot be
     * "paid for" twice — the counter is the price of touching the armour at all.
     */
    @SubscribeEvent
    fun onLivingHurtTaken(e: LivingHurtEvent) {
        val victim = e.entityLiving as? PlayerEntity ?: return
        if (!ElementCombat.wearsSupremacy(victim)) return

        val attacker = e.source.attacker as? LivingEntity ?: return
        if (attacker === victim) return

        val element = ElementType.of(e.source)
        val counter = ElementReactions.counterOf(element) ?: return

        if (ElementCombat.wearsSupremacy(attacker)) {
            FSTLog.log("Supremacy") {
                "element=${element.id}, attacker=${attacker.type.name.string}, " +
                    "outcome=counter-skipped-attacker-has-supremacy"
            }
            return
        }

        ElementAuras.attach(attacker, counter, COUNTER_UNITS)

        FSTLog.log("Supremacy") {
            "element=${element.id}, counter=${counter.id}, attacker=${attacker.type.name.string}, " +
                "units=$COUNTER_UNITS, damage=${"%.2f".format(e.amount)}, " +
                "aura=${ElementAuras.describe(attacker)}, outcome=countered"
        }
    }

    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        if (ElementCombat.isOwnElementalDamage(e)) return

        val attacker = e.source.attacker as? PlayerEntity ?: return
        val target = e.entityLiving
        if (attacker === target) return

        // Checked **before** draining: draining for a non-wearer would throw the memory away for nothing.
        if (!ElementCombat.wearsSupremacy(attacker)) return

        // ⚠️ The swing itself may be about to be discarded by vanilla's i-frame gate. `LivingHurtEvent` fires
        // *before* that gate, so anything queued here would land while the swing does not — exactly the
        // "elemental damage ignores i-frames" report this handler must not re-introduce (`build=cleanup61`).
        // Bail **before** draining: a discarded swing must not eat the remembered reactions either.
        if (ElementCombat.wouldBeDiscardedByIFrames(target, e.source, e.amount)) {
            FSTLog.log("Supremacy") {
                "target=${target.type.name.string}, amount=${"%.2f".format(e.amount)}, " +
                    "outcome=skipped-swing-would-be-gated"
            }
            return
        }

        val totalLevel = ReactionMemory.totalLevel(attacker)

        // Free elemental riders. This block used to sit *after* the replay gate, so it only ran when the
        // player happened to have a reaction stored — which is why the author could not see it working
        // (`build=cleanup56`).
        val riders = totalLevel / RIDER_LEVELS_EACH
        repeat(riders) {
            val pool = ElementType.CONVERTIBLE
            val element = pool[target.world.random.nextInt(pool.size)]
            val riderDamage = e.amount * RIDER_SHARE

            // Two separate concerns, deliberately handled separately (`build=cleanup60`):
            //
            // 1. **damage** — the conversion's 5 % is real damage and must land on every hit. It is queued
            //    directly through `enqueueElemental`, which is the engine's ICD-free path (the same one the
            //    conversion enchantments use). `recordAndReact` cannot be relied on for this: it returns
            //    early when the attachment ICD throttles, and its `damage` argument is only a **reaction
            //    base**, never damage of its own.
            // 2. **attachment** — bypassed (`forceAttach = true`) because 万法 is the mod's own re-dealt
            //    damage, exactly like a poison tick. Without it only every third swing attached anything,
            //    which is what the author saw in `latest.log` (`attachment-throttled, hitsInWindow=11/12/14`).
            ElementCombat.enqueueElemental(attacker, target, element, riderDamage)
            ElementMarks.recordAndReact(
                attacker,
                target,
                element,
                riderDamage,
                units = ElementAuras.WEAK,
                forceAttach = true,
                source = e.source,
            )
        }

        // Reaction memory replay (independent of the riders above).
        val remembered = ReactionMemory.drain(attacker)
        val base = e.amount * REPLAY_SHARE
        var replayed = 0
        for (key in remembered) {
            val reaction = ElementReactions.byKey(key) ?: continue
            ElementAuras.attach(target, reaction.mark, REPLAY_AURA)
            ElementMarks.recordAndReact(
                attacker,
                target,
                reaction.trigger,
                base,
                units = REPLAY_AURA,
                forceAttach = true,
                source = e.source,
            )
            replayed++
        }

        if (riders <= 0 && replayed <= 0) return

        FSTLog.log("Supremacy") {
            "target=${target.type.name.string}, totalLevel=$totalLevel, riders=$riders, " +
                "replay=${remembered.joinToString(",")}, replayed=$replayed, " +
                "base=${"%.2f".format(base)}, outcome=returned"
        }
    }
}
