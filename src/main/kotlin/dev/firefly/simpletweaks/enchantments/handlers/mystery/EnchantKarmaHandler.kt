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
import net.minecraft.entity.player.PlayerEntity
import java.util.WeakHashMap

/**
 * 因果 — the damage you deal is banked, and the bank pays for the damage you take.
 *
 * ## The loop (author's spec, `build=cleanup77`)
 * 1. **Every attack you land resets the pool** to `damage × (20 % + 10 % × level)` — it does not
 *    accumulate, so the pool is "what your last swing was worth", not an ever-growing shield.
 * 2. The pool **expires after `5 + level` seconds**.
 * 3. It can absorb **at most `level` incoming hits** (fewer if it runs out of value first).
 * 4. Absorption is **1:1** on the damage number.
 *
 * At IV: `60 %` of your swing, `9 s` life, up to `4` hits. A big swing therefore buys a real but
 * short-lived shield — and standing still after a hit wastes it.
 *
 * ## Why 1:1 is applied to `LivingHurtEvent.amount`
 * That event is **pre-armour** (`EntityDamageMixin` at the head of `Entity#damage`), so the pool cancels
 * *incoming damage before mitigation*: 100 banked points absorb 100 points of a hit that armour would
 * otherwise have reduced to 20. That is deliberate — "因果" is about the blow itself, and the alternative
 * (post-mitigation at `LivingDamageEvent`) would make the pool 5× stronger against armoured targets for
 * no design reason. The KDoc on [POOL_SHARE_PER_LEVEL] records the choice.
 *
 * ## Why the pool lives in a `WeakHashMap` and not on the item
 * It is transient combat state (seconds), so it must not be serialised: a relog would otherwise restore a
 * shield from a fight that ended yesterday. `WeakHashMap` is keyed by the player entity, so it cannot leak
 * (`AGENTS.md` §4.11's family of bugs), and `Clone`/`logout` clear it explicitly for the cases a `WeakHashMap`
 * alone would keep alive for a moment too long (the discarded entity object).
 */
@ModEnchantment(
    id = "karma",
    category = EnchantCategory.MYSTERY,
    type = EnchantType.WEAPON,
    maxLevel = 4,
    weight = EnchantItemWeights.MYSTERY,
    anvilCost = 18,
    minCostBase = 50,
    supportedItems = EnchantSupportedItemTags.WEAPON,
    slots = [EnchantSlot.MAINHAND],
)
object EnchantKarmaHandler : Listenable {

    /** Pool share of a swing before the per-level part. IV = 60 %. */
    const val POOL_SHARE_BASE = 0.20f
    const val POOL_SHARE_PER_LEVEL = 0.10f

    /** Pool lifetime: `5 + level` seconds. */
    const val LIFETIME_BASE_SECONDS = 5
    const val LIFETIME_PER_LEVEL_SECONDS = 1

    /** Ticks per second, for the lifetime conversion. */
    private const val TICKS_PER_SECOND = 20L

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

    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return

        val attacker = e.attacker

        // ---- (a) fill: this player landed a hit, which *resets* the pool -------------------------
        if (attacker is PlayerEntity && attacker !== e.entityLiving) {
            val level = getItemSpecificEnchantLevel(
                attacker.combatStack(e.source),
                GeneratedEnchantments.KARMA,
            )
            if (level > 0) {
                val share = POOL_SHARE_BASE + POOL_SHARE_PER_LEVEL * level
                val banked = e.amount * share
                val lifetime = (LIFETIME_BASE_SECONDS + LIFETIME_PER_LEVEL_SECONDS * level).toLong()
                POOLS[attacker] = Pool(
                    amount = banked,
                    expiresAt = attacker.world.time + lifetime * TICKS_PER_SECOND,
                    hitsLeft = level,
                )
                FSTLog.log("Karma") {
                    "player=${attacker.name.string}, lvl=$level, hit=${e.amount}, " +
                        "banked=$banked, lifetimeSeconds=$lifetime, hits=$level, outcome=pool-set"
                }
            }
        }

        // ---- (b) spend: this player is the one being hurt ----------------------------------------
        val victim = e.entityLiving as? PlayerEntity ?: return
        if (victim === attacker) return
        val level = getItemSpecificEnchantLevel(victim.mainHandStack, GeneratedEnchantments.KARMA)
        if (level <= 0) return
        val pool = POOLS[victim] ?: return

        val now = victim.world.time
        if (now > pool.expiresAt || pool.hitsLeft <= 0 || pool.amount <= 0f) {
            POOLS.remove(victim)
            FSTLog.log("Karma") {
                "player=${victim.name.string}, age=${now - pool.expiresAt}, hits=${pool.hitsLeft}, " +
                    "left=${pool.amount}, outcome=pool-expired"
            }
            return
        }

        val before = e.amount
        val absorbed = minOf(pool.amount, e.amount)
        e.amount -= absorbed
        pool.amount -= absorbed
        pool.hitsLeft -= 1

        FSTLog.log("Karma") {
            "player=${victim.name.string}, lvl=$level, damage=$before->${e.amount}, " +
                "absorbed=$absorbed, poolLeft=${pool.amount}, hitsLeft=${pool.hitsLeft}, " +
                "outcome=absorbed"
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

    /** Remaining pool for [player]; exposed for the diagnostics command and the game test. */
    @JvmStatic
    fun pooledOf(player: PlayerEntity): Float = POOLS[player]?.amount ?: 0f
}
