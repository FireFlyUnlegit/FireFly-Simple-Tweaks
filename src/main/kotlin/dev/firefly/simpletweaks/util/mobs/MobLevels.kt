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

    /** Armour points that count as one unit of player power. `20` = a full netherite set. */
    private const val ARMOR_REFERENCE = 20.0

    /** Health above the vanilla `20` that counts as one unit of player power. */
    private const val HEALTH_REFERENCE = 20.0

    /** Attack damage that counts as one unit of player power (a base sword is `~7`). */
    private const val ATTACK_REFERENCE = 10.0

    /** Enchantment levels across the player's whole loadout that count as one unit of player power. */
    private const val ENCHANT_LEVELS_REFERENCE = 40.0

    /**
     * Difficulty added per unit of **player power** (see [playerPower]).
     *
     * This is the number that decides whether the mod "keeps up": with `2.0`, an iron-armoured player is
     * worth `+2.6` difficulty (level 10-ish) and a modded netherite player with 60 enchantment levels is
     * worth `+10`, i.e. level 35+. Before `v2.2.2` the gear contribution was a **capped** `1.5 + 1.5`, so
     * the total could never exceed `+3` (+10 levels) no matter how strong the player got — which is why
     * mobs fell behind the moment the player started enchanting.
     */
    private const val PLAYER_POWER_WEIGHT = 2.0f

    /** How far away a player still counts as "the one this mob is scaling against", in blocks. */
    /**
     * How far away a player can be and still count for [effectiveDifficulty].
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
    private const val PLAYER_SCAN_RANGE = 128.0

    /** Slots whose enchantments count toward [playerPower]. Off-hand is skipped: it is usually a torch. */
    private val PLAYER_POWER_SLOTS = arrayOf(
        EquipmentSlot.MAINHAND,
        EquipmentSlot.HEAD,
        EquipmentSlot.CHEST,
        EquipmentSlot.LEGS,
        EquipmentSlot.FEET,
    )

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
    fun effectiveDifficulty(mob: MobEntity, world: ServerWorldAccess, difficulty: LocalDifficulty): Float {
        if (!MobEnhancementConfig.enabled) return difficulty.localDifficulty

        val base = (difficulty.localDifficulty - LOCAL_DIFFICULTY_FLOOR).coerceAtLeast(0f) + difficultyOffset
        // A mob that spawns outside [PLAYER_SCAN_RANGE] still belongs to *someone's* difficulty. Falling
        // back to "the nearest player, however far" keeps the curve continuous instead of dropping a
        // whole region to the bare chunk difficulty (see the KDoc on [PLAYER_SCAN_RANGE]).
        val player = nearestPlayer(mob, world) ?: nearestPlayerAnyDistance(mob, world) ?: return base
        return base + playerPower(player) * PLAYER_POWER_WEIGHT
    }

    /**
     * How strong [player] is, in units of "one full netherite set".
     *
     * ```
     * power = armor / 20                                  (0..1 vanilla, more with modded armour)
     *       + max(0, maxHealth - 20) / 20                 (0..1 at 40 HP, more with health enchantments)
     *       + attackDamage / 10                           (~0.7 for a bare iron sword, ~2 with mod damage)
     *       + enchantmentLevels / 40                      (~1 when the player is fully enchanted)
     * ```
     *
     * Deliberately **uncapped**: the point of the whole term is that a player who keeps upgrading keeps
     * meeting tougher mobs. Each axis is normalised to roughly `1.0` at "fully geared for vanilla", so the
     * number stays meaningful instead of being dominated by whichever stat has the largest numbers.
     *
     * ⚠️ Called once per mob spawn / first load, never per tick. It reads six equipment stacks and their
     * enchantment components; the loop only runs on the spawn path.
     */
    private fun playerPower(player: PlayerEntity): Float {
        val armor = (player.armor / ARMOR_REFERENCE).coerceAtLeast(0.0)
        val health = ((player.maxHealth - 20.0f) / HEALTH_REFERENCE).coerceAtLeast(0.0)
        val attack = (
            player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE) / ATTACK_REFERENCE
            ).coerceAtLeast(0.0)
        val enchants = gearEnchantmentLevels(player) / ENCHANT_LEVELS_REFERENCE
        return (armor + health + attack + enchants).toFloat()
    }

    /**
     * Total enchantment levels on [player]'s hands and armour.
     *
     * Sums **levels**, not enchantment count: `Protection IV` on four pieces is `16` and should count for
     * more than four level-1 rolls. Vanilla's own mob-gear formula ignores enchantments entirely, which is
     * another way the modded player runs away from the curve.
     */
    private fun gearEnchantmentLevels(player: PlayerEntity): Double {
        var total = 0
        for (slot in PLAYER_POWER_SLOTS) {
            val stack = player.getEquippedStack(slot)
            if (stack.isEmpty) continue
            val component = stack.get(DataComponentTypes.ENCHANTMENTS) ?: continue
            if (component.isEmpty) continue
            for (entry in component.enchantments) {
                total += component.getLevel(entry)
            }
        }
        return total.toDouble()
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
