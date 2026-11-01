package dev.firefly.simpletweaks.enchantments.handlers.mythic

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.WorldSide
import dev.firefly.simpletweaks.compat.attacker
import dev.firefly.simpletweaks.compat.event.LivingDamageEvent
import dev.firefly.simpletweaks.compat.event.PlayerEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.event.TickEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.compat.target
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.util.absorptionHeadroomId
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.grantAbsorption
import dev.firefly.simpletweaks.util.removeAbsorptionHeadroom
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.Identifier
import java.util.*
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantItemWeights
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSupportedItemTags.SWORD
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment

/**
 * 1.21 port of `enchantments/handlers/mythic/EnchantHealingBladeHandler.kt`.
 *
 * Line-by-line mapping from 1.12.2:
 * | 1.12.2                                                     | 1.21.1                                                             |
 * |------------------------------------------------------------|--------------------------------------------------------------------|
 * | `net.minecraftforge...LivingDamageEvent`                   | `compat.event.LivingDamageEvent`                                   |
 * | `net.minecraftforge...gameevent.TickEvent`                 | `compat.event.TickEvent`                                           |
 * | `net.minecraftforge...gameevent.PlayerEvent`               | `compat.event.PlayerEvent` (both Forge player events are merged)    |
 * | `net.minecraftforge...entity.player.PlayerEvent.Clone`     | `compat.event.PlayerEvent.Clone`                                    |
 * | `net.minecraftforge...EventPriority`                       | `compat.event.EventPriority`                                       |
 * | `e.attacker` / `e.target` / `e.invalid`                    | same extension names, now `compat.attacker` / `compat.target` / `compat.invalid` |
 * | `attacker.heldItemMainhand`                                | `attacker.mainHandStack` (`method_6047`)                           |
 * | `attacker.uniqueID`                                        | `attacker.uuid` (`EntityLike.getUuid`, method_5667)                |
 * | `attacker.health` / `attacker.maxHealth`                   | unchanged (`method_6032` / `method_6063`)                          |
 * | `attacker.absorptionAmount += added`                       | unchanged synthetic property (`method_6067` / `method_6073`)       |
 * | `attacker.heal(healing)`                                   | unchanged (`method_6025`)                                          |
 * | `p.world.isRemote`                                         | `WorldSide.isClient(p.world)` (field_9236 is a FIELD)              |
 * | `Clone.entityPlayer` (the NEW player)                      | `Clone.player` (the port keeps Forge's direction: player = new)     |
 * | `EnchantHealingBlade` (Enchantment object)                 | `GeneratedEnchantments.HEALING_BLADE` (RegistryKey)                    |
 *
 * The three state maps (`absorptionCounter`, `absorptionAliveTimer`, `healingPool`), the
 * `20 * level` absorption expiry, the `0.05f`-per-hit pool growth / per-tick decay and the
 * `e.amount += healing` self-feedback are all preserved verbatim.
 *
 * ## 🔴 Why the overflow absorption had never worked (fixed here)
 * The ported line was `attacker.absorptionAmount += added`. 1.12.2 writes the field directly; 1.21.1
 * routes it through `LivingEntity#setAbsorptionAmount(float)`, which clamps to
 * `[0, generic.max_absorption]` — and that attribute's **default is 0**, so every grant was discarded
 * while `absorptionCounter` advanced by the *requested* amount. The overflow healing therefore never
 * existed, and the handler's own ceiling check was computed from a number that had never been real.
 * Full bytecode evidence: `AGENTS.md` §4.9, `DEVLOG` `build=cleanup21` §1, and
 * [dev.firefly.simpletweaks.util.AbsorptionUtil]'s KDoc (which also holds the primitive both files
 * now share).
 *
 * The fix follows §4.9's three rules:
 * 1. **Raise the ceiling first** — [grantAbsorption] attaches a temporary `max_absorption` modifier
 *    keyed `simple_tweaks:healing_blade_max_absorption` (per source, so it stacks with
 *    `golden_shield`'s rather than fighting it).
 * 2. **Book the landed delta** — `absorptionCounter` records `after - before`, re-read from the
 *    entity, so a clamp can never desync it again.
 * 3. **A clamp is its own outcome** — `[FST-HealingBlade]` reports
 *    `requested` / `applied` / `outcome=granted|clamped|at-cap|no-overflow`, so "granted nothing" and
 *    "never triggered" are distinguishable.
 *
 * One deliberate difference from `golden_shield`: the headroom here is `foreign + cap`, not `cap`.
 * The clamp applies to the **total**, so a golden apple's share would otherwise silently eat into
 * ours. Retiring the shield subtracts our share *before* dropping the modifier, so the clamp that
 * follows trims ours and leaves the foreign share to the `absorption` effect's own modifier.
 */
@ModEnchantment(
    id = "healing_blade",
    category = EnchantCategory.MYTHIC,
    type = EnchantType.SWORD,
    maxLevel = 8,
    weight = EnchantItemWeights.MYTHIC,
    anvilCost = 12,
    minCostBase = 33,
    minCostPerLevel = 8,
    supportedItems = SWORD,
    slots = [EnchantSlot.MAINHAND],
    damagePerLevel = 1.0,
    order = 43,
)
object EnchantHealingBladeHandler : Listenable {

    private val absorptionCounter = mutableMapOf<UUID, Float>()
    private val absorptionAliveTimer = mutableMapOf<UUID, Int>()
    private val healingPool = mutableMapOf<UUID, Float>()

    /**
     * Ceiling this enchantment needs on `generic.max_absorption`; per source, so it adds to
     * `golden_shield`'s instead of replacing it (see [absorptionHeadroomId]).
     */
    private val HEADROOM_ID: Identifier = absorptionHeadroomId("healing_blade")

    @SubscribeEvent(priority = 50)
    fun onLivingDamage(e: LivingDamageEvent) {
        if (e.invalid) return
        val attacker = e.attacker ?: return
        val id = attacker.uuid

        val level = getItemSpecificEnchantLevel(attacker.mainHandStack, GeneratedEnchantments.HEALING_BLADE)
        if (level <= 0) return

        val dmg = e.amount
        // ⚠️ A non-finite incoming amount must not reach the arithmetic below. `e.amount += healing`
        // writes the result back into the **damage itself**, so one `Infinity` / `NaN` here poisons the
        // target's health permanently: `health = NaN` can never be killed (`health <= 0` is false
        // forever). `build=cleanup11` shipped that ("打不死怪物 / lag on 0hp").
        if (!dmg.isFinite() || dmg <= 0f) return

        val poolBefore = healingPool[id] ?: 0f
        val raw = dmg * (0.1f * level) +
            (e.target.health * 0.0125f * level).coerceAtMost(attacker.health) +
            poolBefore

        // The pool is **self-feeding** (`+5 % of this hit's healing`, preserved from 1.12.2), so a
        // single outlier hit compounds: dozens of riders inside one tick took it to `1e36` and then to
        // `Infinity`. The ceiling is expressed in the wielder's own health, so ordinary play never
        // reaches it.
        val healing = raw.coerceAtMost(attacker.maxHealth * (2f + level))
        if (!healing.isFinite() || healing <= 0f) return

        healingPool[id] = (poolBefore + healing * 0.05f).coerceAtMost(attacker.maxHealth)

        // Outcome of the absorption half, reported by the probe at the end of this method. Without it
        // "granted nothing" (a clamp) and "there was no overflow to grant" look identical in the log,
        // which is the failure shape §4.4/§4.9 exist to prevent.
        var requested = 0f
        var applied = 0f
        var absorptionOutcome = "no-overflow"

        val diff = attacker.maxHealth - attacker.health
        if (healing <= diff) {
            attacker.heal(healing)
        } else {
            attacker.heal(diff)

            val mine = absorptionCounter[id] ?: 0f
            // Our own ceiling for the overflow shield (`maxHealth × (0.2 + 0.1 × level)`). Named
            // `overflowCap` rather than the original `maxAbsorption` so it cannot be confused with the
            // `generic.max_absorption` attribute the probe prints next to it.
            val overflowCap = attacker.maxHealth * (0.2f + level * 0.1f)
            val capacity = overflowCap - mine
            if (capacity > 0f) {
                requested = (healing - diff).coerceAtMost(capacity)
                // The clamp is on the total, so a foreign share (a golden apple) has to be covered by
                // the headroom too — otherwise it silently eats into ours. Over-estimating is harmless
                // (the ceiling only permits absorption, it never adds any).
                val foreign = (attacker.absorptionAmount - mine).coerceAtLeast(0f)
                applied = grantAbsorption(attacker, HEADROOM_ID, requested, foreign + overflowCap)
                if (applied > 0f) {
                    absorptionCounter[id] = mine + applied
                    absorptionAliveTimer[id] = 20 * level
                }
                absorptionOutcome = when {
                    applied >= requested -> "granted"
                    applied > 0f -> "clamped"
                    else -> "no-headroom"
                }
            } else {
                absorptionOutcome = "at-cap"
            }
        }

        e.amount += healing
        FSTLog.log("HealingBlade") {
            "attacker=${attacker.name.string}, target=${e.target.name.string}, lvl=$level, damage=$dmg, " +
                "healing=$healing, health=${attacker.health}/${attacker.maxHealth}, " +
                "absorption=${attacker.absorptionAmount}, healedAmount=${healing.coerceAtMost(diff)}, " +
                "pool=${healingPool[id]}, outgoingDamage=${e.amount}, " +
                "requested=$requested, applied=$applied, mine=${absorptionCounter[id] ?: 0f}, " +
                "maxAbsorption=${attacker.maxAbsorption}, outcome=$absorptionOutcome"
        }
    }

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.invalid) return
        val p = e.player
        if (WorldSide.isClient(p.world)) return

        val id = p.uuid

        val pool = healingPool[id]
        if (pool != null) {
            val next = pool - pool * 0.05f
            if (next > 0.01f) healingPool[id] = next else healingPool.remove(id)
        }

        val tracked = absorptionCounter[id]
        if (tracked == null) {
            // Nothing of ours is outstanding, so the ceiling must not stay raised: a leftover modifier
            // would let *any* absorption source overshoot vanilla's own bound. A cheap no-op (one
            // modifier lookup) whenever we never attached one.
            removeAbsorptionHeadroom(p, HEADROOM_ID)
            absorptionAliveTimer.remove(id)
            return
        }

        val current = p.absorptionAmount

        if (current < tracked) {
            if (current <= 0f) {
                absorptionCounter.remove(id)
                absorptionAliveTimer.remove(id)
                // Fully spent, so ours is gone and there is nothing left to protect.
                removeAbsorptionHeadroom(p, HEADROOM_ID)
            } else {
                absorptionCounter[id] = current
            }
            return
        }

        val timer = absorptionAliveTimer[id] ?: run {
            absorptionCounter.remove(id)
            removeAbsorptionHeadroom(p, HEADROOM_ID)
            return
        }

        val remaining = timer - 1
        if (remaining <= 0) {
            // Subtract *our* share while the raised ceiling is still in place, then retire it. Doing it
            // the other way round would let the clamp trim the foreign share instead of ours (the
            // vanilla `absorption` effect keeps its own modifier, so what survives is not clamped to 0).
            p.absorptionAmount = (current - tracked).coerceAtLeast(0f)
            absorptionCounter.remove(id)
            absorptionAliveTimer.remove(id)
            val modifierRemoved = removeAbsorptionHeadroom(p, HEADROOM_ID)
            FSTLog.log("HealingBlade") {
                "player=${p.name.string}, absorption=${p.absorptionAmount}, released=$tracked, " +
                    "modifierRemoved=$modifierRemoved, outcome=absorption-expired"
            }
        } else {
            absorptionAliveTimer[id] = remaining
        }
    }

    @SubscribeEvent
    fun onLogout(e: PlayerEvent.PlayerLoggedOutEvent) {
        val id = e.player.uuid
        absorptionCounter.remove(id)
        absorptionAliveTimer.remove(id)
        healingPool.remove(id)
        clearContribution(e.player)
    }


    @SubscribeEvent
    fun onLogin(e: PlayerEvent.PlayerLoggedInEvent) {
        clearContribution(e.player)
    }

    @SubscribeEvent
    fun onClone(e: PlayerEvent.Clone) {
        clearContribution(e.original)
        clearContribution(e.player)
    }

    private fun clearContribution(p: PlayerEntity) {
        val id = p.uuid
        val tracked = absorptionCounter[id] ?: 0f
        if (tracked > 0f) {
            // Subtract first, then retire the ceiling — see the expiry branch above for why the order
            // matters (the clamp that follows must trim our share, not a foreign one).
            p.absorptionAmount = (p.absorptionAmount - tracked).coerceAtLeast(0f)
        }
        removeAbsorptionHeadroom(p, HEADROOM_ID)
        absorptionCounter.remove(id)
        absorptionAliveTimer.remove(id)
        healingPool.remove(id)
    }
}
