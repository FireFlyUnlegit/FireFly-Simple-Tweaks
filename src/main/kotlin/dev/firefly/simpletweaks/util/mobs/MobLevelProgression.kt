package dev.firefly.simpletweaks.util.mobs

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.attacker
import dev.firefly.simpletweaks.compat.event.LivingDeathEvent
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.core.config.MobEnhancementConfig
import net.minecraft.entity.mob.MobEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.world.ServerWorld
import kotlin.math.max

/**
 * Mobs **level up while the fight is happening** instead of only at spawn.
 *
 * ## Why this exists
 * `MobLevels` decides a level once, when the mob is created. That makes an encounter static: a mob
 * that has been chewing on a geared player for two minutes is exactly as strong as it was in the first
 * second. This object adds three chances for a levelled mob to grow *during* play — a "the longer you
 * let it live, the worse it gets" pressure that a spawn-time roll cannot express.
 *
 * ## The three triggers
 * | trigger | event | default |
 * |---|---|---|
 * | the mob hits a player | [LivingHurtEvent][onLivingHurt] | `3 %` |
 * | the mob kills anything | [LivingDeathEvent][onLivingDeath] | `10 %` |
 * | a player hurts the mob and it survives | [LivingHurtEvent][onLivingHurt] | `1 %` |
 *
 * All three are **independent** 0-100 knobs in `MobEnhancementConfig` (`/stconfig` page 5); `0` turns
 * one trigger off without disturbing the others, which is what makes "calm mobs, but kills still
 * promote" expressible.
 *
 * ## Why the cap is 1.5 × the level this spot would spawn at
 * An uncapped level-up is a runaway: a mob parked next to a player would climb forever, and a single
 * tanky zombie would eventually out-scale the entire enchantment tree. The cap re-uses the **spawn**
 * formula ([MobLevels.previewDifficulty]) so it automatically follows the player's gear and the chunk's
 * difficulty — the room to grow is "50 % above what this position would have spawned", not an absolute
 * number that goes stale when the player upgrades. The `10..100` clamp only keeps the early game
 * (where `previewDifficulty` is tiny) and the absurd late game sane.
 *
 * ## Why the level-up writes through [MobLevels.apply]
 * A mob that levelled up in combat must be indistinguishable from one that spawned at that level —
 * same health, attack, armour **and** the knockback resistance added in `build=cleanup67`. Duplicating
 * the attribute writes here would let the two paths drift, and `apply` is already idempotent (fixed
 * modifier ids, remove-then-add), so "level up" is literally "apply the new level".
 */
object MobLevelProgression : Listenable {

    /** How far above the spawn-time level a mob may climb, as a multiple of it. */
    private const val CAP_MULTIPLIER = 1.5

    /** Lower bound for the cap, so early-game mobs are not capped at level 2-3. */
    private const val MIN_CAP = 10

    /** Upper bound for the cap: past this the numbers stop being interesting and start being a wall. */
    private const val MAX_CAP = 100

    /**
     * Player hurt by, or hurting, a mob.
     *
     * The two directions live in one listener on purpose: they are the same seam, and splitting them
     * would register two `LivingHurtEvent` handlers where one private helper decides the direction.
     *
     * `e.source.attacker` (not `trueSource`) is the right accessor for "who is holding the weapon" —
     * arrows are excluded for free this way, because a projectile's attacker is the shooter but the
     * *mob* is the one that must grow here.
     */
    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        val attacker = e.attacker
        val target = e.entityLiving

        // Direction 1: a mob hit a player.
        if (target is PlayerEntity && attacker is MobEntity) {
            roll(attacker, MobEnhancementConfig.levelUpOnHitPlayer, "hit-player")
            return
        }

        // Direction 2: a player hurt a mob. `target.isAlive` is true here because `LivingHurtEvent`
        // fires before the damage lands; a lethal hit kills the mob a few statements later, and that
        // death is the "kill" trigger's business, not this one's.
        if (target is MobEntity && attacker is PlayerEntity && target.isAlive) {
            roll(target, MobEnhancementConfig.levelUpWhenHurt, "hurt-by-player")
        }
    }

    /**
     * A mob killed something.
     *
     * `e.entityLiving` is the **victim**, so the growing mob is `e.source.attacker`. A mob killing an
     * unlevelled mob (or a player killing a mob) both come through here; [roll] filters the killer.
     */
    @SubscribeEvent
    fun onLivingDeath(e: LivingDeathEvent) {
        if (e.invalid) return
        val killer = e.source.attacker as? MobEntity ?: return
        if (killer === e.entityLiving) return
        roll(killer, MobEnhancementConfig.levelUpOnKill, "kill")
    }

    /**
     * Percent chance for [mob] to gain one level, `reason` being which trigger fired.
     *
     * Deliberately **one** level per event: a mob that gets hit ten times in a second should not jump
     * from 10 to 20, it should have ten independent `1 %` chances. That keeps the growth rate
     * proportional to how much punishment the mob is actually absorbing.
     */
    private fun roll(mob: MobEntity, percent: Int, reason: String) {
        if (percent <= 0) return
        if (!MobEnhancementConfig.enabled) return
        if (!MobEnhancementConfig.levelsEnabled) return
        if (!mob.isAlive) return

        val levelled = mob as? LevelledMob ?: return
        val current = levelled.mobLevel
        // `0` means the spawn path never levelled this mob (feature off, or a non-mob carrier). Growing
        // it would create a level with none of the spawn-time calibration behind it.
        if (current <= 0) return

        if (mob.random.nextFloat() * 100f >= percent) return

        val world = mob.world as? ServerWorld ?: return
        val player = world.getClosestPlayer(mob, MobLevels.PLAYER_SCAN_RANGE)
        val spawnLevel = MobLevels.averageLevelFor(
            MobLevels.previewDifficulty(world, mob.blockPos, player)
        )
        val cap = (spawnLevel * CAP_MULTIPLIER).toInt().coerceIn(MIN_CAP, MAX_CAP)

        if (current >= cap) {
            // Distinct outcome, not a silent return: "the roll passed but the mob is capped" and "the
            // roll failed" look identical from the outside, and `AGENTS.md` §4.4 is exactly about that.
            FSTLog.log("MobLevelProgression") {
                "mob=${mob.type.name.string}, level=$current, cap=$cap, reason=$reason, " +
                    "outcome=capped"
            }
            return
        }

        val next = current + 1
        levelled.mobLevel = next
        // Re-running the spawn-time write is what makes the new level real (see this object's KDoc).
        MobLevels.apply(mob, next)

        FSTLog.log("MobLevelProgression") {
            // `max(spawnLevel, 1)` guards the `%.1f`/`%d` split: `spawnLevel` is an `Int`, so it must be
            // `$` interpolated, never `%.1f` (`AGENTS.md` §4.6: a formatting crash here kills the tick).
            "mob=${mob.type.name.string}, level=$current->$next, cap=$cap, " +
                "spawnLevel=${max(spawnLevel, 1)}, reason=$reason, " +
                "health=${"%.1f".format(mob.maxHealth)}, outcome=levelled-by-$reason"
        }
    }
}
