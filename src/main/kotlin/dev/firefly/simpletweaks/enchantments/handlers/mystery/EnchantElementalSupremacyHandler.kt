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
import dev.firefly.simpletweaks.util.elements.ReactionMemory
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
    maxLevel = 1,
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

    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        if (ElementCombat.isOwnElementalDamage(e)) return

        val attacker = e.source.attacker as? PlayerEntity ?: return
        val target = e.entityLiving
        if (attacker === target) return

        // Cheap gate first: only a wearer has anything stored.
        val remembered = ReactionMemory.drain(attacker)
        if (remembered.isEmpty()) return

        // The enchantment may have been removed between recording and swinging.
        if (!ElementCombat.wearsSupremacy(attacker)) return

        val base = e.amount * REPLAY_SHARE
        var replayed = 0
        for (key in remembered) {
            val reaction = ElementReactions.byKey(key) ?: continue
            // Attach the mark the reaction needs, then let the engine resolve it.
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

        FSTLog.log("Supremacy") {
            "target=${target.type.name.string}, replay=${remembered.joinToString(",")}, " +
                "replayed=$replayed, base=${"%.2f".format(base)}, outcome=returned"
        }
    }
}
