package dev.firefly.simpletweaks.util.mobs

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.core.config.MobEnhancementConfig
import net.minecraft.component.DataComponentTypes
import net.minecraft.entity.EquipmentSlot
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
 * ## Formula (`v2.2.2`)
 * ```
 * worldTerm          = max(0, localDifficulty - 1.2) + difficultyOffset   (how old/hard the chunk is)
 * playerPower        = armor/20 + max(0, maxHealth-20)/20 + attackDamage/10 + enchantmentLevels/40
 * effectiveDifficulty = worldTerm + playerPower × 2.0
 * level              = 1 + floor(effectiveDifficulty / 0.30)              (NO upper bound)
 * ```
 * Three deliberate choices:
 *
 * 1. **No level cap.** The previous version clamped to `1..10`, which made late-game mobs stop scaling
 *    entirely. What bounds the level now is only the two inputs above, both of which keep growing.
 * 2. **A brand-new chunk means level 1-2, not level 6.** `1.2` is subtracted from vanilla's
 *    `localDifficulty` because its floor (`2 × 0.75 = 1.5` on `NORMAL`) is *not* "difficulty", it is the
 *    baseline every fresh chunk has. See [LOCAL_DIFFICULTY_FLOOR].
 * 3. **The player's whole loadout is the second half of the difficulty** — armour, max health, attack
 *    damage and enchantment levels, uncapped, so upgrading keeps raising the mobs. See [playerPower].
 *
 * ## Stats, per level above 1
 * | attribute | operation | amount |
 * |---|---|---|
 * | `generic.max_health` | `ADD_MULTIPLIED_BASE` | `+0.20` (+20 %) |
 * | `generic.attack_damage` | `ADD_MULTIPLIED_BASE` | `+0.15` (+15 %) |
 * | `generic.armor` | `ADD_VALUE` | `+0.4` |
 * | `generic.knockback_resistance` | `ADD_VALUE` | `+0.03`, capped at `0.95` |
 *
 * At level 10 that is **+180 % health, +135 % attack damage, +3.6 armour, +27 % knockback
 * resistance**; the growth is linear and unbounded (except for the knockback cap), and armour's own
 * damage-reduction curve softens the high end (vanilla caps armour's effect well before the numbers
 * get silly). The numbers are [HEALTH_PER_LEVEL] / [ATTACK_PER_LEVEL] / [ARMOR_PER_LEVEL] /
 * [KNOCKBACK_PER_LEVEL] — the table above is a restatement of those constants, so change the constants
 * and the table together.
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

    /** `+20 %` max health per level above 1. */
    const val HEALTH_PER_LEVEL = 0.2

    /** `+15 %` attack damage per level above 1. */
    const val ATTACK_PER_LEVEL = 0.15

    /** `+0.4` armour per level above 1. */
    const val ARMOR_PER_LEVEL = 0.4

    /**
     * `+0.03` knockback resistance per level above 1, **capped at [KNOCKBACK_CAP]**.
     *
     * Knockback resistance is a plain fraction in `0..1`: `LivingEntity#takeKnockback` multiplies the
     * applied velocity by `1 - resistance`, so `1.0` would make a mob literally unmovable. `0.95` is
     * the deliberate ceiling — a high-level mob shrugs off almost every push, but a critical-hit
     * knockback still budges it, which keeps "shove it into the lava" a viable answer.
     *
     * ⚠️ The vanilla attribute `generic.knockback_resistance` is a `ClampedEntityAttribute` with a
     * **hard max of `1.0`** (see `AGENTS.md` §4.9). `0.95` sits inside that clamp, so this write needs
     * no headroom trick — unlike `absorptionAmount`, which silently discards everything above
     * `generic.max_absorption`.
     */
    const val KNOCKBACK_PER_LEVEL = 0.03

    /** Ceiling for the knockback resistance this system grants. See [KNOCKBACK_PER_LEVEL]. */
    const val KNOCKBACK_CAP = 0.95

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
     * `localDifficulty` of a **brand-new chunk**, i.e. the floor that must be subtracted before a level is
     * computed.
     *
     * Vanilla's `LocalDifficulty` constructor (bytecode, 1.21.1) ends in
     * `return difficulty.getId() * f`, where `f` starts at `0.75` and grows with the time of day, the
     * moon phase and the chunk's inhabited time. So a fresh chunk at `NORMAL` is `2 × 0.75 = 1.5` **before
     * anything has happened in the world** — and with `DIFFICULTY_PER_LEVEL = 0.30` that is level 6, which
     * is exactly the "新进游戏就是 Lv7~9" the author reported. Subtracting the floor is what makes level 1
     * mean "nothing has happened yet".
     *
     * `1.2` rather than the exact `1.5`: it leaves a fresh (`NORMAL`) chunk at `0.3` → **level 2**, keeps
     * `EASY` (`0.75`) at level 1, and puts `HARD` (`2.25`) at level 4 — difficulty still matters, without
     * handing a new player level-6 mobs.
     */
    private const val LOCAL_DIFFICULTY_FLOOR = 1.2f

    /**
     * Difficulty added per unit of **player power** (see [playerPower]).
     *
     * This is the number that decides whether the mod "keeps up": with `2.0`, an iron-armoured player is
     * worth `+2.6` difficulty (level 10-ish) and a modded netherite player with 60 enchantment levels is
     * worth `+10`, i.e. level 35+. Before `v2.2.2` the gear contribution was a **capped** `1.5 + 1.5`, so
     * the total could never exceed `+3` (+10 levels) no matter how strong the player got — which is why
     * mobs fell behind the moment the player started enchanting.
     */
    private const val PLAYER_POWER_WEIGHT = 1.5f

    /** How far away a player still counts as "the one this mob is scaling against", in blocks. */
    /**
     * How far away a player can be and still count for [effectiveDifficulty] — and, since
     * `build=cleanup67`, also the radius [MobLevelProgression] uses to decide which player's gear the
     * mid-fight level cap should follow. Public for that reason; keep one number for both.
     *
     * **128**, not 64, and that is a bug fix (`build=cleanup24`). Vanilla's natural spawner only keeps
     * mobs away from players within `SpawnHelper`'s `24.0` minimum (`javap` on 1.21.1: `ldc2_w 576.0` =
     * `24²`), but it *attempts* spawns anywhere in the loaded chunks — routinely 70–120 blocks out.
     * With a 64-block scan those spawns found **no player**, fell back to the bare chunk difficulty
     * (`0.0–0.3`) and produced level 1–2 mobs, while a `/summon`-ed or egg-spawned mob sat right next to
     * the player and got the full gear-based bonus. That is exactly the reported "只有指令生成的等级才正确".
     *
     * 128 is also the outer limit a hostile mob will path towards a player from, so it matches "the
     * player this mob is going to meet" rather than an arbitrary radius.
     */
    const val PLAYER_SCAN_RANGE = 128.0

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
    private val KNOCKBACK_ID = Identifier.of("simple_tweaks", "mob_level_knockback")

    /**
     * The difficulty the level is actually computed from: how far the **world** has aged, plus how strong
     * the **nearest player** is.
     *
     * ## Why the player's stats belong in here
     * Vanilla's local difficulty knows nothing about how strong the player is. A player in full enchanted
     * netherite and a player in leather used to get exactly the same mobs, which is why the level system
     * alone could not keep a late-game fight interesting — once the player out-geared the difficulty
     * curve, every mob was trivial again.
     *
     * ## The two calibration errors this fixes (`v2.2.2`)
     * 1. **Too high at the start.** `localDifficulty` on `NORMAL` is `2 × 0.75 = 1.5` in a chunk nobody has
     *    ever been in (see [LOCAL_DIFFICULTY_FLOOR]), and `/0.30` turned that into **level 6** before the
     *    player had done anything. Subtracting the floor makes "nothing has happened yet" mean level 1-2.
     * 2. **Too flat later.** The player's gear contributed a **capped** `1.5 + 1.5 = 3.0` difficulty — ten
     *    levels, forever — so the moment the player started enchanting, mobs fell behind. Gear now
     *    contributes a continuous [playerPower] term with no per-stat cap.
     *
     * ## Why the raw getter and not `clampedLocalDifficulty`
     * `getClampedLocalDifficulty()` in 1.21.1 is not a number in difficulty units at all: its bytecode is
     * `(localDifficulty - 2) / 2` clamped to `0..1`, i.e. a **progress** value used by vanilla's mob-equipment
     * formulas. `getLocalDifficulty()` is the raw regional value and is what the level has to be derived
     * from. (An earlier note here claimed the clamped getter stops at `6.75`; that was wrong, and it is
     * part of why the original `0.30` divisor was chosen against a `0.13` measurement.)
     *
     * Returns the difficulty, **not** the level, because the same number feeds the enchant and element
     * probabilities — so a well-geared player meets tougher mobs on all three axes at once instead of only
     * one.
     */
    /**
     * The **difficulty number a mob would get before any player-power term**.
     *
     * `= max(localDifficulty - LOCAL_DIFFICULTY_FLOOR, 0) + difficultyOffset + difficultyModifier`.
     *
     * Single home for that sum on purpose: `/fst difficulty check` reports this exact figure, and the
     * spawn path consumes it through [effectiveDifficulty]. Two copies of the formula would let the
     * reporting command disagree with the game — the "键存在 ≠ 有人读它" family of bugs.
     *
     * `difficultyOffset` is the **session-only** `/fst difficulty <n>` knob; `difficultyModifier` is the
     * **persistent** `/fst difficulty modify <n>` one (config-backed).
     */
    fun baseDifficulty(localDifficulty: Float): Float =
        (localDifficulty - LOCAL_DIFFICULTY_FLOOR).coerceAtLeast(0f) +
            difficultyOffset +
            MobEnhancementConfig.difficultyModifier

    /**
     * What [player] standing at [pos] would make a mob spawn at — the number `/fst difficulty check`
     * prints.
     *
     * Same two terms as [effectiveDifficulty] (base + player power × weight), just computed from a
     * position and a player instead of from an entity that already exists.
     */
    fun previewDifficulty(world: net.minecraft.server.world.ServerWorld, pos: net.minecraft.util.math.BlockPos, player: PlayerEntity?): Float {
        val base = baseDifficulty(world.getLocalDifficulty(pos).localDifficulty)
        if (!MobEnhancementConfig.enabled || player == null) return base
        return base + playerPower(player) * PLAYER_POWER_WEIGHT
    }

    fun effectiveDifficulty(mob: MobEntity, world: ServerWorldAccess, difficulty: LocalDifficulty): Float {
        if (!MobEnhancementConfig.enabled) return difficulty.localDifficulty

        val base = baseDifficulty(difficulty.localDifficulty)
        // A mob that spawns outside [PLAYER_SCAN_RANGE] still belongs to *someone's* difficulty. Falling
        // back to "the nearest player, however far" keeps the curve continuous instead of dropping a
        // whole region to the bare chunk difficulty (see the KDoc on [PLAYER_SCAN_RANGE]).
        val player = nearestPlayer(mob, world) ?: nearestPlayerAnyDistance(mob, world) ?: return base
        return base + playerPower(player) * PLAYER_POWER_WEIGHT
    }

    /**
     * How strong [player] is, in units of "one fully geared vanilla player".
     *
     * The definition moved to [dev.firefly.simpletweaks.util.PlayerPower] (`build=cleanup76`) because the
     * **reaction damage** curve now reads the same number: one player-strength definition for both the
     * mob difficulty curve and 剧变 / 激化. This method stays as the internal name `MobLevels` uses, so
     * the difficulty formula below reads unchanged.
     *
     * ⚠️ Called once per mob spawn / first load, never per tick.
     */
    private fun playerPower(player: PlayerEntity): Float =
        dev.firefly.simpletweaks.util.PlayerPower.of(player).toFloat()

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

    /** Nearest player at **any** distance; the fallback for spawns outside [PLAYER_SCAN_RANGE]. */
    private fun nearestPlayerAnyDistance(mob: MobEntity, world: ServerWorldAccess): PlayerEntity? {
        val worldView = world as? World ?: return null
        var best: PlayerEntity? = null
        var bestSq = Double.MAX_VALUE
        for (player in worldView.players) {
            val sq = player.squaredDistanceTo(mob)
            if (sq < bestSq) {
                bestSq = sq
                best = player
            }
        }
        return best
    }

    /**
     * The difficulty-derived level **before** the random spread, i.e. the mean of [levelFor].
     *
     * At [DIFFICULTY_PER_LEVEL] `0.30` (`v2.2.2` calibration):
     *
     * | situation | effectiveDifficulty | level |
     * |---|---|---|
     * | brand-new `NORMAL` chunk, naked player | `0.3 + 0.1 × 2` | **2** |
     * | same, iron armour + a few enchants | `0.3 + 1.3 × 2` | **9** |
     * | same, diamond + ~25 enchant levels | `0.3 + 2.7 × 2` | **19** |
     * | fully modded netherite + 60 enchant levels | `0.3 + 5.3 × 2` | **36** |
     * | long-inhabited `HARD` chunk | `+1.8` on top of the above | higher still |
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

    /**
     * Writes [level]'s stat modifiers onto [mob] and heals it to the **new** maximum.
     *
     * Public because the mid-fight level-up path ([MobLevelProgression]) has to re-run exactly this
     * logic: a levelled-up mob must gain the same stats a mob that spawned at that level would have.
     * Every modifier is `removeModifier(id)` + `addPersistentModifier(...)` with a **fixed** id, so a
     * second call for the same level is an overwrite, not a stack — that is what makes "level up by 1"
     * safe to express as "call [apply] again with the new level".
     *
     * ⚠️ It **full-heals**: the heal exists because a fresh mob sits at the old maximum, and a mob that
     * just levelled up is meant to feel like it renewed itself. The heal is therefore part of the
     * feature, not a side effect people should "fix" without asking.
     */
    fun apply(mob: MobEntity, level: Int) {
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

        // Knockback resistance is an `ADD_VALUE` fraction, so it needs its own `coerceAtMost` — the
        // operation has no cap of its own beyond the attribute's `0..1` clamp, and 1.0 would make the
        // mob immovable. See [KNOCKBACK_CAP].
        mob.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE)?.let { instance ->
            instance.removeModifier(KNOCKBACK_ID)
            val resistance = (steps * KNOCKBACK_PER_LEVEL).coerceIn(0.0, KNOCKBACK_CAP)
            if (resistance > 0.0) {
                instance.addPersistentModifier(
                    EntityAttributeModifier(
                        KNOCKBACK_ID, resistance,
                        EntityAttributeModifier.Operation.ADD_VALUE,
                    )
                )
            }
        }

        // A mob whose max health just went up would otherwise spawn at the OLD maximum (a level-10 zombie
        // would look almost dead). This is the "write + read back" half of `AGENTS.md` §4.9: after
        // touching max_health, set current health from the *new* value rather than assuming it.
        mob.health = mob.maxHealth
    }
}
