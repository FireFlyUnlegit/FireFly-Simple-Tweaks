package dev.firefly.simpletweaks.enchantments.handlers.mystery

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.attacker
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.PlayerEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantItemWeights
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSupportedItemTags
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.combatStack
import dev.firefly.simpletweaks.util.damagesource.isAuxiliaryDamage
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.player.PlayerEntity
import java.util.WeakHashMap

/**
 * 因果 — the damage you deal is banked, and the bank pays for the damage you take.
 *
 * ## The loop (author's spec)
 * 1. **Every attack you land resets the pool** to `damage × (20 % + 10 % × level)` — it does not
 *    accumulate, so the pool is "what your last swing was worth", not an ever-growing shield.
 * 2. The pool **expires after `5 + level` seconds**.
 * 3. It can absorb **at most `level` incoming hits** (fewer if it runs out of value first).
 * 4. Absorption is **1:1** on the damage number.
 *
 * At effective level IV: `60 %` of your swing, `9 s` life, up to `4` hits.
 *
 * ## Why `maxLevel = 16` — "one full set" (`build=cleanup78`)
 * The enchantment is per-piece, so the ceiling the numbers are designed against is **four pieces at IV**
 * = 16. Declaring 16 as the enchantment's own `max_level` means a perfect set is *exactly* a max-level
 * enchantment instead of four items pretending to be level 4, and `/fst enchant <target> karma 16 armor`
 * is the natural "give me a full set" command.
 *
 * The **equipped level is the sum** across the four pieces, clamped to [MAX_LEVEL], and every formula
 * uses `sum / `[PIECES] — i.e. the *effective* level is still `0..4`. Two consequences worth stating:
 *
 * - Four pieces at IV (`4 × 4 = 16`) gives effective `4` — the designed maximum.
 * - One piece forced to 16 also gives effective `4`, not 16. Deliberate: otherwise a single command
 *   would hand out `0.20 + 0.10 × 16 = 180 %` of a swing and 16 free hits, which is 3× the design.
 *
 * ## Why an **armour** enchantment at all
 * The first version was `MAINHAND`-only. In the author's test session it was installed with
 * `/fst enchant @s karma 4 armor`, i.e. on the four armour pieces — so neither branch ever matched the
 * main hand and the enchantment produced **zero log lines and zero effect**. That is a usability trap,
 * not a maths error: "the damage I take is absorbed" reads as protection, and protection belongs on
 * armour.
 *
 * ## Why 1:1 is applied to `LivingHurtEvent.amount`
 * That event is **pre-armour** (`EntityDamageMixin` at the head of `Entity#damage`), so the pool cancels
 * *incoming damage before mitigation*: 100 banked points absorb 100 points of a hit that armour would
 * otherwise have reduced to 20. That is deliberate — 因果 is about the blow itself.
 *
 * ## Only a real swing fills the pool (`build=cleanup79`)
 * The fill branch ignores **auxiliary** damage. Riders (conversion, reaction, DoT, arcs) are credited to
 * the player too, and they are much smaller than a swing — without the guard they overwrote the pool a
 * few milliseconds after the swing that created it, which is why the first test round looked like "the
 * effect does nothing" while the log showed 606 fills and 33 absorbs.
 *
 * ## Why the pool lives in a `WeakHashMap` and not on the item
 * It is transient combat state (seconds), so it must not be serialised: a relog would otherwise restore a
 * shield from a fight that ended yesterday. `WeakHashMap` is keyed by the player entity, so it cannot leak
 * (`AGENTS.md` §4.11's family of bugs), and `Clone`/logout clear it explicitly.
 */
@ModEnchantment(
    id = "karma",
    category = EnchantCategory.MYSTERY,
    type = EnchantType.ARMOR,
    maxLevel = 16,
    weight = EnchantItemWeights.MYSTERY,
    anvilCost = 18,
    minCostBase = 50,
    supportedItems = EnchantSupportedItemTags.ARMOR,
    slots = [EnchantSlot.ARMOR],
)
object EnchantKarmaHandler : Listenable {

    /** `4 pieces × 4 levels`: the ceiling the numbers are designed against. */
    const val MAX_LEVEL = 16

    /** Armour pieces the equipped level is summed over. */
    const val PIECES = 4

    /** The effective (per-piece) level range, i.e. `MAX_LEVEL / PIECES`. */
    const val MAX_EFFECTIVE_LEVEL = MAX_LEVEL / PIECES

    /** Pool share of a swing before the per-level part. IV = 60 %. */
    const val POOL_SHARE_BASE = 0.20f
    const val POOL_SHARE_PER_LEVEL = 0.10f

    /** Pool lifetime: `5 + level` seconds. */
    const val LIFETIME_BASE_SECONDS = 5
    const val LIFETIME_PER_LEVEL_SECONDS = 1

    /** Ticks per second, for the lifetime conversion. */
    private const val TICKS_PER_SECOND = 20L

    /** The four slots the level is read from. */
    private val ARMOR_SLOTS = arrayOf(
        EquipmentSlot.HEAD,
        EquipmentSlot.CHEST,
        EquipmentSlot.LEGS,
        EquipmentSlot.FEET,
    )

    /** The banked damage and the two ways it can run out. */
    private class Pool(
        /** Remaining absorption points. */
        var amount: Float,
        /** Tick after which the pool is void. */
        val expiresAt: Long,
        /** Incoming hits it may still absorb. */
        var hitsLeft: Int,
    )

    private val POOLS = WeakHashMap<PlayerEntity, Pool>()

    /**
     * Equipped level, **summed** over the four armour pieces and clamped to [MAX_LEVEL].
     *
     * Public and taking the sum rather than the player so the clamp is testable: a game test has no
     * player, and "16 means a full set, not one item" is exactly the rule that must not silently change.
     */
    fun equippedLevelOf(sumOfPieces: Int): Int = sumOfPieces.coerceIn(0, MAX_LEVEL)

    /** The effective per-piece level used by every formula: `0..`[MAX_EFFECTIVE_LEVEL]. */
    fun effectiveLevelOf(level: Int): Float = level.toFloat() / PIECES

    private fun equippedLevel(player: PlayerEntity): Int {
        var sum = 0
        for (slot in ARMOR_SLOTS) {
            sum += getItemSpecificEnchantLevel(player.getEquippedStack(slot), GeneratedEnchantments.KARMA)
        }
        return equippedLevelOf(sum)
    }

    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return

        val attacker = e.attacker

        // ---- (a) fill: this player landed a hit, which *resets* the pool -------------------------
        //
        // ⚠️ `isAuxiliaryDamage()` guard is load-bearing (`build=cleanup79`). Without it **every**
        // damage instance this player is credited with reset the pool — elemental conversion riders,
        // reaction damage, venom DoT ticks, chain arcs — and those are usually far smaller than a swing.
        // The measured log was 606 `pool-set` lines against 33 `absorbed`: a 106 hit banked 63.6, then
        // the same swing's 5.30 and 0.75 riders immediately replaced it with 3.18 and 0.45. The pool
        // was being destroyed by the player's own maths, which is exactly why "因果效果不明显".
        // "每次攻击都会重置" means every *attack*, not every damage event.
        if (attacker is PlayerEntity && attacker !== e.entityLiving && !e.source.isAuxiliaryDamage()) {
            val effective = effectiveLevelOf(equippedLevel(attacker))
            if (effective > 0f) {
                val share = POOL_SHARE_BASE + POOL_SHARE_PER_LEVEL * effective
                val banked = e.amount * share
                val lifetime = (LIFETIME_BASE_SECONDS + LIFETIME_PER_LEVEL_SECONDS * effective).toInt()
                POOLS[attacker] = Pool(
                    amount = banked,
                    expiresAt = attacker.world.time + lifetime * TICKS_PER_SECOND,
                    hitsLeft = effective.toInt().coerceAtLeast(1),
                )
                FSTLog.log("Karma") {
                    "player=${attacker.name.string}, effective=${"%.2f".format(effective)}, " +
                        "hit=${"%.2f".format(e.amount)}, banked=${"%.2f".format(banked)}, " +
                        "lifetimeSeconds=$lifetime, hits=${effective.toInt()}, outcome=pool-set"
                }
            }
        }

        // ---- (b) spend: this player is the one being hurt ----------------------------------------
        val victim = e.entityLiving as? PlayerEntity ?: return
        if (victim === attacker) return
        val effective = effectiveLevelOf(equippedLevel(victim))
        if (effective <= 0f) return
        val pool = POOLS[victim] ?: return

        val now = victim.world.time
        if (now > pool.expiresAt || pool.hitsLeft <= 0 || pool.amount <= 0f) {
            POOLS.remove(victim)
            FSTLog.log("Karma") {
                "player=${victim.name.string}, effective=${"%.2f".format(effective)}, " +
                    "expiredBy=${now - pool.expiresAt}, " +
                    "hitsLeft=${pool.hitsLeft}, poolLeft=${"%.2f".format(pool.amount)}, " +
                    "outcome=pool-void"
            }
            return
        }

        val before = e.amount
        val absorbed = minOf(pool.amount, e.amount)
        e.amount -= absorbed
        pool.amount -= absorbed
        pool.hitsLeft -= 1

        FSTLog.log("Karma") {
            "player=${victim.name.string}, effective=${"%.2f".format(effective)}, " +
                "damage=$before->${e.amount}, " +
                "absorbed=${"%.2f".format(absorbed)}, poolLeft=${"%.2f".format(pool.amount)}, " +
                "hitsLeft=${pool.hitsLeft}, outcome=absorbed"
        }

        if (pool.amount <= 0f || pool.hitsLeft <= 0) POOLS.remove(victim)
    }

    @SubscribeEvent
    fun onClone(e: PlayerEvent.Clone) {
        POOLS.remove(e.original)
        POOLS.remove(e.player)
    }

    @SubscribeEvent
    fun onLogout(e: PlayerEvent.PlayerLoggedOutEvent) {
        // Explicit body, not `= POOLS.remove(...)`: that expression's inferred type is the private
        // `Pool`, which Kotlin rejects as a public return type.
        POOLS.remove(e.player)
    }

    /** Remaining pool for [player]; exposed for diagnostics and the game test. */
    @JvmStatic
    fun pooledOf(player: PlayerEntity): Float = POOLS[player]?.amount ?: 0f
}
