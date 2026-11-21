package dev.firefly.simpletweaks.util

import net.minecraft.component.DataComponentTypes
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.player.PlayerEntity
import kotlin.math.sqrt

/**
 * How strong a player is, in units of **"one fully geared vanilla player"**.
 *
 * ```
 * power = armor / 40                                   (0.5 at vanilla 20 armour)
 *       + max(0, maxHealth - 20) / 20                  (0..1 at 40 HP, more with health enchantments)
 *       + attackDamage / 20                            (~0.35 for a bare iron sword, ~2 with mod damage)
 *       + enchantScore / 24                            (see [enchantScore])
 * ```
 *
 * A naked player is `~0.1`, iron gear `~1.1`, diamond + a few enchantments `~2.5`, a fully modded
 * netherite build `~7`.
 *
 * ## Why this lives in `util/` and not inside one feature
 * Two unrelated systems need the same number and must not disagree:
 *
 * | consumer | use |
 * |---|---|
 * | `MobLevels#effectiveDifficulty` | [dev.firefly.simpletweaks.util.mobs.MobLevels.playerDifficultyTerm] |
 * | `ElementMarks` (剧变 / 激化 reactions) | `power × 5` replaces the old **experience level** as the damage driver |
 *
 * The reaction side used to read `player.experienceLevel`, which is a **farmable currency** (a mob farm
 * takes it from 0 to 100+ without the player fighting anything), so a reaction's damage tracked the
 * player's AFK time instead of their gear. Gear power cannot be farmed: it is the armour, health,
 * weapon damage and enchantments the player is wearing. Sharing one definition also means the mob curve
 * and the reaction curve keep up with the same upgrades.
 *
 * ## Why uncapped
 * The point of the term is that a player who keeps upgrading keeps facing (and dealing) more. Each axis
 * is normalised to roughly `1.0` at "fully geared for vanilla", so the number stays meaningful instead of
 * being dominated by whichever stat has the largest numbers. Consumers that need a ceiling apply their
 * own — `ElementMarks` bounds its reaction power, `MobLevels` deliberately does not.
 *
 * ⚠️ Cost: this reads five equipment stacks and their enchantment components. It is called **once per
 * mob spawn** and **once per reaction** — never per tick.
 */
object PlayerPower {

    /** Armour points that count as one unit of power. Higher = base gear matters less. */
    private const val ARMOR_REFERENCE = 40.0

    /** Health above the vanilla `20` that counts as one unit. */
    private const val HEALTH_REFERENCE = 20.0

    /** Attack damage that counts as one unit (a base sword is `~7`). */
    private const val ATTACK_REFERENCE = 20.0

    /** Enchantment score that counts as one unit. Lower = enchantments matter more. */
    private const val ENCHANT_SCORE_REFERENCE = 24.0

    /** Distinct enchantments that cost nothing extra; breadth only counts beyond this. */
    private const val BREADTH_FREE = 5

    /** Score per distinct enchantment beyond [BREADTH_FREE]. */
    private const val BREADTH_PER_ENCHANT = 0.8

    /** A common enchantment is worth exactly one unit. */
    private const val QUALITY_MIN = 1.0

    /** Ceiling for [qualityWeight]; keeps one mythic book from dwarfing the whole loadout. */
    private const val QUALITY_MAX = 2.2

    /** Slots whose enchantments count. Off-hand is skipped: it is usually a torch. */
    private val SLOTS = arrayOf(
        EquipmentSlot.MAINHAND,
        EquipmentSlot.HEAD,
        EquipmentSlot.CHEST,
        EquipmentSlot.LEGS,
        EquipmentSlot.FEET,
    )

    /** The four terms behind [of], so `/fst stats` can show where the number comes from. */
    data class Parts(
        val armor: Double,
        val health: Double,
        val attack: Double,
        val enchants: Double,
        val enchantCount: Int,
    ) {
        val total: Double get() = armor + health + attack + enchants
    }

    /** @see PlayerPower */
    @JvmStatic
    fun of(player: PlayerEntity): Double = partsOf(player).total

    /** @see PlayerPower */
    @JvmStatic
    fun partsOf(player: PlayerEntity): Parts {
        val armor = (player.armor / ARMOR_REFERENCE).coerceAtLeast(0.0)
        val health = ((player.maxHealth - 20.0f) / HEALTH_REFERENCE).coerceAtLeast(0.0)
        val attack = (
            player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE) / ATTACK_REFERENCE
            ).coerceAtLeast(0.0)
        val (score, count) = enchantScoreOf(player)
        return Parts(armor, health, attack, score / ENCHANT_SCORE_REFERENCE, count)
    }

    /**
     * How much one level of an enchantment with [weight] is worth, relative to a common one.
     *
     * `weight` is vanilla's rarity weight (10 = common, 5 = uncommon, 2 = rare, 1 = very rare) and this
     * mod sets it from its own tier table (`EnchantItemWeights`), so a mythic enchantment lands on the
     * ceiling without a second lookup table. `sqrt` keeps the ladder legible — `10 / weight` would make
     * one legendary book worth ten commons.
     */
    @JvmStatic
    fun qualityWeight(weight: Int): Double =
        sqrt(10.0 / weight.coerceAtLeast(1)).coerceIn(QUALITY_MIN, QUALITY_MAX)

    /**
     * Enchantment **score** of [player]'s hands and armour: `level × quality`, plus a small bonus for
     * having many distinct enchantments.
     *
     * Why not raw levels (what this used to count): a full vanilla set is ~26 levels, and a modded set is
     * also ~60 levels — the two looked far closer than they play. Counting quality makes one mythic
     * enchantment weigh as much as two commons, which is what the author asked for ("根据附魔数量以及
     * 品质提升 lvl 的权重提高"). Counting **count** separately rewards breadth: ten level-1 enchantments
     * across five pieces is a stronger loadout than three level-3 rolls, and raw levels cannot tell them
     * apart.
     */
    private fun enchantScoreOf(player: PlayerEntity): Pair<Double, Int> {
        var score = 0.0
        var count = 0
        for (slot in SLOTS) {
            val stack = player.getEquippedStack(slot)
            if (stack.isEmpty) continue
            val component = stack.get(DataComponentTypes.ENCHANTMENTS) ?: continue
            if (component.isEmpty) continue
            for (entry in component.enchantments) {
                val level = component.getLevel(entry)
                score += level * qualityWeight(entry.value().weight)
                count++
            }
        }
        if (count > BREADTH_FREE) {
            score += (count - BREADTH_FREE) * BREADTH_PER_ENCHANT
        }
        return score to count
    }
}
