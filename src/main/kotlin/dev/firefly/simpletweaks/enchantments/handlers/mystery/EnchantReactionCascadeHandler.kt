package dev.firefly.simpletweaks.enchantments.handlers.mystery

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.*
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.elements.ElementAuras
import dev.firefly.simpletweaks.util.elements.ElementCombat
import dev.firefly.simpletweaks.util.elements.ElementMarks
import dev.firefly.simpletweaks.util.elements.ElementReactions
import dev.firefly.simpletweaks.util.elements.ElementType
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import java.util.WeakHashMap

/**
 * 万法归寂 — the hit **ignites a reaction** on whatever aura the target already carries, ignoring the
 * attachment ICD.
 *
 * ## The gap it fills
 * Every reaction in this mod is triggered *by the element you deal*. A fire-conversion player therefore can
 * only ever produce the reactions fire participates in — the rest of the 19-row table is somebody else's
 * build. This enchantment reverses the direction: it looks at the aura the target **already has** and picks
 * the strongest reaction that aura can produce, so one weapon can finish reactions it could never start.
 *
 * ## Why it is not `detonate`
 * `detonate` consumes the aura for raw elemental damage. This one runs the real **reaction** — the reaction
 * name, its status effects (armour shred, blind, freeze …) and the engine's amplifying / transformative
 * maths all apply, which is why the numbers can exceed `detonate` on a good aura.
 *
 * ## The 0.5 s internal cooldown (author's request)
 * Without it, a high-attack-speed weapon would re-trigger the same reaction every swing and the damage log
 * would become unreadable. *Transformative* reactions are additionally throttled to 1 s by
 * `ElementMarks.TRANSFORMATIVE_ICD_TICKS`, so the real ceiling is the engine's, not this constant — the
 * 0.5 s window exists mainly to bound the amplifying path.
 */
@ModEnchantment(
    id = "reaction_cascade",
    category = EnchantCategory.MYSTERY,
    type = EnchantType.SWORD,
    maxLevel = 3,
    weight = EnchantItemWeights.MYSTERY,
    anvilCost = 90,
    minCostBase = 30,
    minCostPerLevel = 30,
    supportedItems = EnchantSupportedItemTags.SWORD,
    slots = [EnchantSlot.MAINHAND],
    damagePerLevel = 2.5,
    order = 54,
)
object EnchantReactionCascadeHandler : Listenable {

    /** 0.5 s per attacker (author's request). */
    private const val COOLDOWN_TICKS = 10L

    /** Damage fed into the reaction: `hit × (0.4 + 0.2 × level)`. */
    private const val BASE_SHARE = 0.4f
    private const val PER_LEVEL = 0.2f

    private val lastCascade = WeakHashMap<PlayerEntity, Long>()

    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        // Our own elemental riders arrive as their own hits; they must not cascade again.
        if (ElementCombat.isOwnElementalDamage(e)) return

        val attacker = e.source.attacker as? PlayerEntity ?: return
        val target = e.entityLiving
        if (attacker === target) return

        val level = getItemSpecificEnchantLevel(attacker.mainHandStack, GeneratedEnchantments.REACTION_CASCADE)
        if (level <= 0) return

        val now = target.world.time
        val last = lastCascade[attacker]
        if (last != null && now - last < COOLDOWN_TICKS) return

        val aura = ElementAuras.peek(target) ?: return
        val trigger = bestTrigger(aura.first) ?: return
        val reaction = ElementReactions.of(aura.first, trigger) ?: return

        lastCascade[attacker] = now
        val damage = e.amount * (BASE_SHARE + PER_LEVEL * level)

        // `forceAttach = true`: the whole point is to ignore the attachment ICD. The reaction itself is
        // resolved by the engine, so its amplification / transformative maths stays in one place.
        val outcome = ElementMarks.recordAndReact(
            attacker,
            target,
            trigger,
            damage,
            units = ElementAuras.WEAK * level,
            forceAttach = true,
            source = e.source,
        )

        FSTLog.log("ReactionCascade") {
            "target=${target.type.name.string}, level=$level, aura=${aura.first.id}, trigger=${trigger.id}, " +
                "reaction=${reaction.key}, damage=${"%.2f".format(damage)}, " +
                "amplify=${"%.2f".format(outcome.amplify)}, flat=${"%.2f".format(outcome.flatDamage)}, " +
                "outcome=cascaded"
        }
    }

    /**
     * The strongest reaction the target's aura can produce, or `null` when nothing reacts with it.
     *
     * "Strongest" = highest `amplify + transformativeCoeff`, which prefers real damage reactions over the
     * control ones (a freeze on an ice aura is nice, but a melt is what the player expects from a mystery
     * enchantment). Ties are broken by the enum order, so the outcome is deterministic — a random pick would
     * make the damage numbers look buggy.
     */
    private fun bestTrigger(aura: ElementType): ElementType? =
        ElementType.entries
            .filter { it != aura }
            .mapNotNull { candidate -> ElementReactions.of(aura, candidate)?.let { candidate to it } }
            .filter { (_, reaction) -> reaction.amplify > 1f || reaction.transformativeCoeff > 0f }
            .maxByOrNull { (_, reaction) -> reaction.amplify + reaction.transformativeCoeff }
            ?.first
}
