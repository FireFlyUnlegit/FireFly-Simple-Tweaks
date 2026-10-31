package dev.firefly.simpletweaks.util.mobs

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.core.config.MobEnhancementConfig
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.mob.MobEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.Identifier
import net.minecraft.util.math.random.Random
import net.minecraft.world.LocalDifficulty
import net.minecraft.world.ServerWorldAccess
import net.minecraft.world.World
import kotlin.math.floor
import kotlin.math.max

/**
 * The mob level system: difficulty decides a level, the level decides base stats.
 *
 * ## Formula
 * ```
 * effectiveDifficulty = localDifficulty                      (unclamped, grows with inhabited time)
 *                     + min(armor / 20, 1) × 1.5             (nearest player's gear)
 *                     + min((maxHealth - 20) / 20, 1) × 1.5  (nearest player's health)
 * level              = 1 + floor(effectiveDifficulty / 0.30)  (NO upper bound)
 * ```
 * Two deliberate choices, both requested by the author:
 *
 * 1. **No level cap.** The previous version clamped to `1..10`, which made late-game mobs stop scaling
 *    entirely. The cap is gone; what bounds the level now is only vanilla's own inputs to
 *    `localDifficulty`, which keep growing as a chunk accumulates inhabited time.
 * 2. **The player's armour and max health are part of the difficulty.** Without this, a player who
 *    out-gears the difficulty curve faces trivial mobs forever: vanilla's local difficulty knows nothing
 *    about what the player is wearing. See [effectiveDifficulty] for the weighting and the reason it uses
 *    the *unclamped* getter.
 *
 * ## Stats, per level above 1
 * | attribute | operation | amount |
 * |---|---|---|
 * | `generic.max_health` | `ADD_MULTIPLIED_BASE` | `+0.10` (+10 %) |
 * | `generic.attack_damage` | `ADD_MULTIPLIED_BASE` | `+0.08` (+8 %) |
 * | `generic.armor` | `ADD_VALUE` | `+1.0` |
 *
 * At level 10 that is **+90 % health, +72 % attack damage, +9 armour**; the growth above that is linear
 * and unbounded, and armour's own damage-reduction curve softens the high end (vanilla caps armour's
 * effect well before the numbers get silly).
 *
 * ## Why `addPersistentModifier`
 * The buff **is** part of this entity's permanent state: it is decided once at spawn and must survive a
 * chunk reload. That is exactly the `saved = true` case in `AGENTS.md` §4.2, unlike the element system's
 * armour shred, whose duration lives in memory and therefore must be temporary.
 *
 * The three modifier ids are **fixed per attribute** (not per level, not per source): re-applying would
 * otherwise stack, and the guard below is the only thing preventing a double application. Fixed ids make
 * a second application idempotent instead of additive.
 */
object MobLevels {

    /** `+10 %` max health per level above 1. */
    const val HEALTH_PER_LEVEL = 0.10

    /** `+8 %` attack damage per level above 1. */
    const val ATTACK_PER_LEVEL = 0.08

    /** `+1` armour per level above 1. */
    const val ARMOR_PER_LEVEL = 1.0

    /**
     * `clampedLocalDifficulty` per level above 1.
     *
     * ⚠️ Was `1.5`, i.e. `level = 1 + floor(difficulty × 1.5)`. In a real session `difficulty` measured
     * **0.13** (near spawn / freshly loaded chunks), which yields level **1 for every mob in the world** —
     * the feature looked completely dead, and because level 1 logs nothing, there was no evidence either
     * way. `0.30` reaches level 2 at difficulty `0.30` and level 10 (the cap) at `2.70`, both inside what
     * normal play actually produces; vanilla's own clamp (6.75) sits above that and is simply saturated.
     */
    private const val DIFFICULTY_PER_LEVEL = 0.30

    /** How far a level may deviate from the difficulty-derived value, as a fraction of it. */
    private const val LEVEL_SPREAD = 0.25f

    /**
     * Armour points at which [ARMOR_WEIGHT] is fully earned. `20` = a full netherite set.
     */
    private const val ARMOR_REFERENCE = 20.0

    /** Difficulty added by a fully armoured player. */
    private const val ARMOR_WEIGHT = 1.5

    /** Health above the vanilla `20` at which [HEALTH_WEIGHT] is fully earned. */
    private const val HEALTH_REFERENCE = 20.0

    /** Difficulty added by a player with double the base health. */
    private const val HEALTH_WEIGHT = 1.5

    /** How far away a player still counts as "the one this mob is scaling against", in blocks. */
    private const val PLAYER_SCAN_RANGE = 64.0

    /**
     * Session-only difficulty offset, added to every mob's effective difficulty.
     *
     * Set by `/simple_tweaks difficulty <value>` and **deliberately not persisted**: it exists to answer
     * "what do mobs look like at difficulty 5?" without having to find a long-inhabited hard-difficulty
     * chunk. Restarting the game clears it, which is the right default for a tuning aid.
     *
     * It feeds the **same** number as everything else, so raising it also raises the enchant and element
     * chances — that is intentional: one knob to see the whole feature.
     */
    @JvmStatic
    var difficultyOffset: Float = 0f

    private val HEALTH_ID = Identifier.of("simple_tweaks", "mob_level_health")
    private val ATTACK_ID = Identifier.of("simple_tweaks", "mob_level_attack")
    private val ARMOR_ID = Identifier.of("simple_tweaks", "mob_level_armor")

    /**
     * The difficulty the level is actually computed from: vanilla's local difficulty **plus a term for the
     * nearest player's gear**.
     *
     * ## Why the player's stats belong in here
     * Vanilla's `clampedLocalDifficulty` knows nothing about how strong the player is. A player in full
     * enchanted netherite and a player in leather get exactly the same mobs, which is the reason the
     * level system alone cannot keep a late-game fight interesting — after the player out-gears the
     * difficulty curve, every mob is trivial again.
     *
     * ## Which number and why it is not the clamped one
     * This uses **[LocalDifficulty.getLocalDifficulty]**, the *unclamped* value. The clamped variant stops
     * at `6.75`, which would put a hard ceiling on the level (the level cap this feature is explicitly not
     * supposed to have). The unclamped value keeps growing as chunks accumulate inhabited time.
     *
     * ## Bounds
     * The player terms are each capped at their weight (`1.5 + 1.5`), so a modded player with 10 000 HP
     * cannot drive the level into absurd territory on their own; only vanilla's own growth can, and that
     * growth is what the player experiences as "the world getting harder".
     *
     * Returns the difficulty, **not** the level, because the same number feeds the enchant and element
     * probabilities — so a well-geared player meets tougher mobs on all three axes at once instead of only
     * one.
     */
    fun effectiveDifficulty(mob: MobEntity, world: ServerWorldAccess, difficulty: LocalDifficulty): Float {
        if (!MobEnhancementConfig.enabled) return difficulty.localDifficulty

        val base = difficulty.localDifficulty + difficultyOffset
        val player = nearestPlayer(mob, world) ?: return base

        val armorTerm = (player.armor / ARMOR_REFERENCE).coerceIn(0.0, 1.0) * ARMOR_WEIGHT
        val healthTerm =
            ((player.maxHealth - 20.0f) / HEALTH_REFERENCE).coerceIn(0.0, 1.0) * HEALTH_WEIGHT
        return (base + armorTerm + healthTerm).toFloat()
    }

    /**
     * Nearest player within [PLAYER_SCAN_RANGE], or `null`.
     *
     * ⚠️ Called **once per mob spawn**, not per slot and not per tick — `effectiveDifficulty` is invoked
     * once by the mixin and its result is passed to all three features. `getClosestPlayer` itself is a
     * scan over the players in this world, which is why it must not be moved onto a tick seam.
     */
    private fun nearestPlayer(mob: MobEntity, world: ServerWorldAccess): PlayerEntity? {
        val worldView = world as? World ?: return null
        return worldView.getClosestPlayer(mob.x, mob.y, mob.z, PLAYER_SCAN_RANGE, false)
    }

    /**
     * The level [difficulty] maps to. **No upper bound** — see [effectiveDifficulty] for why that is
     * intentional.
     *
     * At [DIFFICULTY_PER_LEVEL] `0.30`: a mob next to a naked player near spawn (`0.13`) is level 1, one
     * near a fully kitted player (`0.13 + 3.0`) is level 11, and a mob in a long-inhabited hard-difficulty
     * chunk can go far beyond that.
     */
    /**
     * The difficulty-derived level **before** the random spread, i.e. the mean of [levelFor].
     *
     * Exposed so `/simple_tweaks difficulty` can report a number without having to spawn a mob: it prints
     * "a mob spawned at exactly this offset would be about level N", which is what the author is tuning.
     */
    fun averageLevelFor(difficulty: Float): Int =
        1 + floor(difficulty / DIFFICULTY_PER_LEVEL).toInt()
    fun levelFor(difficulty: Float, random: Random): Int {
        val base = averageLevelFor(difficulty)
        // **Levels float within a range** instead of being a pure function of the difficulty. Two mobs
        // spawning in the same spot used to be exactly the same level, which made a group read as one
        // repeated number; a ±25 % band (at least ±1) gives a pack a spread without changing its average.
        val spread = max(1, (base * LEVEL_SPREAD).toInt())
        val rolled = base + random.nextInt(spread * 2 + 1) - spread
        // `coerceAtLeast(1)` is what keeps a negative/NaN input from producing a nonsense level. There is
        // deliberately **no upper bound** — the level is meant to grow without a ceiling.
        return rolled.coerceAtLeast(1)
    }

    /**
     * Assigns a level to [mob] once, at spawn, and applies its stat modifiers.
     *
     * Idempotent: a mob that already has a level (including one read back from NBT) is left alone. That
     * guard is load-bearing — `enchantEquipment`, which is where this is called from, runs once per
     * armour slot **plus** once for the main hand, i.e. up to five times per spawn.
     */
    fun ensureLevel(mob: MobEntity, effectiveDifficulty: Float, random: Random) {
        if (!MobEnhancementConfig.enabled) return
        if (!MobEnhancementConfig.levelsEnabled) return
        val levelled = mob as? LevelledMob ?: return
        if (levelled.mobLevel > 0) return

        val level = levelFor(effectiveDifficulty, random)
        levelled.mobLevel = level
        if (level <= 1) {
            // Debug-gated, and the only way to tell "this mob is genuinely level 1" apart from "the
            // spawn seam never ran". Before this line existed, a whole session could produce zero
            // `[FST-MobLevel]` output with no way to distinguish the two — `AGENTS.md` §4.4.
            FSTLog.log("MobLevel") {
                "mob=${mob.type.name.string}, level=1, " +
                    "difficulty=${"%.2f".format(effectiveDifficulty)}, " +
                    "outcome=mob-level-1-no-buff"
            }
            return
        }

        apply(mob, level)

        FSTLog.log("MobLevel") {
            // ⚠️ `LivingEntity#getArmor` returns an **Int**, so it must be `%d`. It was `%.1f` and threw
            // `IllegalFormatConversionException: f != java.lang.Integer` on the very first levelled mob —
            // which crashed the world tick, because this line runs on the mob-spawn path. A formatting
            // crash inside a debug probe is not "only affects debug builds": it kills the game whenever
            // the probe is enabled.
            "mob=${mob.type.name.string}, level=$level, " +
                "difficulty=${"%.2f".format(effectiveDifficulty)}, " +
                "health=${"%.1f".format(mob.maxHealth)}, armor=${mob.armor}, " +
                "outcome=mob-levelled"
        }
    }

    private fun apply(mob: MobEntity, level: Int) {
        val steps = (level - 1).toDouble()

        // `ADD_MULTIPLIED_BASE` multiplies the *base* value, so the bonus is a stable +N % of the mob's
        // own base rather than a compounding multiplier — re-applying (or another mod adding a modifier)
        // cannot turn it into exponential growth.
        mob.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH)?.let { instance ->
            instance.removeModifier(HEALTH_ID)
            instance.addPersistentModifier(
                EntityAttributeModifier(
                    HEALTH_ID, steps * HEALTH_PER_LEVEL,
                    EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE,
                )
            )
        }
        mob.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE)?.let { instance ->
            instance.removeModifier(ATTACK_ID)
            instance.addPersistentModifier(
                EntityAttributeModifier(
                    ATTACK_ID, steps * ATTACK_PER_LEVEL,
                    EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE,
                )
            )
        }
        mob.getAttributeInstance(EntityAttributes.GENERIC_ARMOR)?.let { instance ->
            instance.removeModifier(ARMOR_ID)
            instance.addPersistentModifier(
                EntityAttributeModifier(
                    ARMOR_ID, steps * ARMOR_PER_LEVEL,
                    EntityAttributeModifier.Operation.ADD_VALUE,
                )
            )
        }

        // A mob whose max health just went up would otherwise spawn at the OLD maximum (a level-10 zombie
        // would look almost dead). This is the "write + read back" half of `AGENTS.md` §4.9: after
        // touching max_health, set current health from the *new* value rather than assuming it.
        mob.health = mob.maxHealth
    }
}
