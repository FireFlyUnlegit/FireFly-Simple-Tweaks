package dev.firefly.simpletweaks.enchantments.handlers.mystery

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantItemWeights
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSupportedItemTags
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.elements.ElementDamageSeam
import dev.firefly.simpletweaks.util.elements.ReactionSeam
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import java.util.WeakHashMap

/**
 * 共鸣核心 — reactions stack 共鸣 on the target, and the stack amplifies your elemental damage against it.
 *
 * ## The loop
 * 1. A reaction you trigger adds **one 共鸣 stack** to that target (cap `2 + level`, i.e. `3..5`).
 * 2. Stacks last **10 s** and refresh on every new reaction.
 * 3. Your elemental damage against that target is multiplied by `1 + 0.04 × stacks`
 *    (up to `+20 %` at 5 stacks).
 * 4. At the **cap**, the next elemental hit against that target is a **guaranteed crit** (`×2`) and the
 *    stacks are consumed.
 *
 * ## Why the amplification lives in a seam and not in the engine
 * `ElementCombat.enqueueElemental` is the funnel every elemental rider goes through, but the engine must
 * not know about enchantment ids. [ElementDamageSeam] is the "ask the listeners" hole added for this
 * (`build=cleanup83`), so `resonance_core` scales damage without the engine importing it — the same
 * arrangement `ReactionSeam` uses for the reaction event.
 *
 * ## Ordering that matters
 * The stack for a reaction is added **after** that reaction's own damage was queued (the seam runs at
 * enqueue time, the stack is added from the `ReactionSeam` callback afterwards). So a reaction never
 * amplifies itself — only your *next* one does.
 *
 * ## Why stacks are per-target and not per-element
 * "该元素伤害" is served by the stack itself: a reaction already fixed which element is in play, and
 * making the buff per-element would let a target be at 5 stacks of fire and 5 of water at once, i.e. 10
 * invisible numbers the player cannot read. One stack counter per target, decaying in 10 s, is something
 * a player can actually track.
 */
@ModEnchantment(
    id = "resonance_core",
    category = EnchantCategory.MYSTERY,
    type = EnchantType.WEAPON,
    maxLevel = 3,
    weight = EnchantItemWeights.MYSTERY,
    anvilCost = 18,
    minCostBase = 50,
    supportedItems = EnchantSupportedItemTags.WEAPON,
    slots = [EnchantSlot.MAINHAND],
)
object EnchantResonanceCoreHandler : Listenable {

    /** Stacks a level adds on top of the base two. III = 5 stacks. */
    const val STACKS_PER_LEVEL = 1
    const val BASE_STACKS = 2

    /** Elemental damage bonus per stack. */
    const val BONUS_PER_STACK = 0.04f

    /** Stacks lifetime in ticks (10 s). */
    const val STACK_TICKS = 200L

    /** Multiplier applied by the guaranteed crit at full stacks. */
    const val CRIT_MULTIPLIER = 2f

    /** One target's stack state. */
    private class Resonance(val stacks: Int, val expiresAt: Long)

    private val STACKS = WeakHashMap<LivingEntity, Resonance>()

    /** Stack cap for [level]. Public and pure so it is testable without a player. */
    fun capFor(level: Int): Int = BASE_STACKS + STACKS_PER_LEVEL * level.coerceIn(0, 3)

    /** Damage multiplier for [stacks], capped at the strongest possible cap. Public and pure. */
    fun multiplierFor(stacks: Int): Float =
        1f + BONUS_PER_STACK * stacks.coerceIn(0, capFor(MAX_LEVEL))

    /** The declared `max_level`; also the level the pure helpers are tested at. */
    const val MAX_LEVEL = 3

    override fun init() {
        ReactionSeam.register { attacker, target, _, _ -> addStack(attacker, target) }
        ElementDamageSeam.register { attacker, target, _, amount -> amplify(attacker, target, amount) }
    }

    /** Adds one stack after a reaction the wearer triggered on [target]. */
    private fun addStack(attacker: LivingEntity?, target: LivingEntity) {
        val player = attacker as? PlayerEntity ?: return
        if (player.world.isClient) return
        val level = getItemSpecificEnchantLevel(player.mainHandStack, GeneratedEnchantments.RESONANCE_CORE)
        if (level <= 0) return

        val cap = capFor(level)
        val now = target.world.time
        val current = STACKS[target]
        val stacks = if (current != null && current.expiresAt > now) current.stacks else 0
        if (stacks >= cap) return

        STACKS[target] = Resonance(stacks + 1, now + STACK_TICKS)
        FSTLog.log("ResonanceCore") {
            "player=${player.name.string}, target=${target.type.name.string}, lvl=$level, " +
                "stacks=${stacks + 1}/$cap, outcome=stack-gained"
        }
    }

    /** Multiplies [amount] of elemental damage from [attacker] onto [target]. */
    private fun amplify(attacker: LivingEntity?, target: LivingEntity, amount: Float): Float {
        val player = attacker as? PlayerEntity ?: return amount
        val level = getItemSpecificEnchantLevel(player.mainHandStack, GeneratedEnchantments.RESONANCE_CORE)
        if (level <= 0) return amount
        val resonance = STACKS[target] ?: return amount
        val now = target.world.time
        if (resonance.expiresAt <= now) {
            STACKS.remove(target)
            FSTLog.log("ResonanceCore") {
                "target=${target.type.name.string}, stacks=${resonance.stacks}, outcome=expired"
            }
            return amount
        }

        // The cap belongs to the *current* weapon's level: a player who swaps to a lower-level copy must
        // not keep a 5-stack crit that its own enchantment could never build.
        val cap = capFor(level)
        val effective = resonance.stacks.coerceAtMost(cap)
        val multiplier = multiplierFor(effective)
        if (effective < cap) {
            FSTLog.log("ResonanceCore") {
                "target=${target.type.name.string}, stacks=$effective, " +
                    "multiplier=$multiplier, outcome=amplified"
            }
            return amount * multiplier
        }

        // Full stacks: this hit crits and the resonance is spent.
        STACKS.remove(target)
        FSTLog.log("ResonanceCore") {
            "target=${target.type.name.string}, stacks=$effective, " +
                "multiplier=$multiplier, crit=$CRIT_MULTIPLIER, outcome=critical-and-consumed"
        }
        return amount * multiplier * CRIT_MULTIPLIER
    }

    /** Current stacks on [target], for diagnostics and the game test. */
    @JvmStatic
    fun stacksOf(target: LivingEntity): Int = STACKS[target]?.stacks ?: 0
}
