package dev.firefly.simpletweaks.util

import dev.firefly.simpletweaks.util.mobs.MobLevels
import net.minecraft.component.DataComponentTypes
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.player.PlayerEntity

/**
 * How strong a player is, in units of **"one fully geared vanilla player"**.
 *
 * ```
 * power = armor / 20                                   (0..1 vanilla, more with modded armour)
 *       + max(0, maxHealth - 20) / 20                  (0..1 at 40 HP, more with health enchantments)
 *       + attackDamage / 10                            (~0.6 for a bare iron sword, ~2 with mod damage)
 *       + enchantmentLevels / 40                       (~1 when the player is fully enchanted)
 * ```
 *
 * A naked player is `~0.1`, iron gear `~1.3`, diamond + a few enchantments `~2.7`, a fully modded
 * netherite build `~5.3`.
 *
 * ## Why this lives in `util/` and not inside one feature
 * Two unrelated systems need the same number and must not disagree:
 *
 * | consumer | use |
 * |---|---|
 * | `MobLevels#effectiveDifficulty` | `power × 2.0` is added to the difficulty a spawning mob gets |
 * | `ElementMarks` (剧变 / 激化 reactions) | `power × 5` replaces the old **experience level** as the damage driver |
 *
 * The reaction side used to read `player.experienceLevel`, which is a **farmable currency** (a mob farm
 * takes it from 0 to 100+ without the player fighting anything), so a reaction's damage tracked the
 * player's AFK time instead of their gear. Gear power cannot be farmed: it is the armour, health,
 * weapon damage and enchantment levels the player is wearing. Sharing one definition also means the
 * mob curve and the reaction curve keep up with the same upgrades.
 *
 * ## Why uncapped
 * The point of the term is that a player who keeps upgrading keeps facing (and dealing) more. Each axis
 * is normalised to roughly `1.0` at "fully geared for vanilla", so the number stays meaningful instead of
 * being dominated by whichever stat has the largest numbers. Consumers that need a ceiling apply their
 * own — `ElementMarks` bounds its reaction power, `MobLevels` deliberately does not.
 *
 * ⚠️ Cost: this reads six equipment stacks and their enchantment components. It is called **once per
 * mob spawn** and **once per reaction** — never per tick.
 */
object PlayerPower {

    /** Armour points that count as one unit of power. `20` = a full netherite set. */
    private const val ARMOR_REFERENCE = 20.0

    /** Health above the vanilla `20` that counts as one unit. */
    private const val HEALTH_REFERENCE = 20.0

    /** Attack damage that counts as one unit (a base sword is `~7`). */
    private const val ATTACK_REFERENCE = 10.0

    /** Enchantment levels across the loadout that count as one unit. */
    private const val ENCHANT_LEVELS_REFERENCE = 40.0

    /** Slots whose enchantments count. Off-hand is skipped: it is usually a torch. */
    private val SLOTS = arrayOf(
        EquipmentSlot.MAINHAND,
        EquipmentSlot.HEAD,
        EquipmentSlot.CHEST,
        EquipmentSlot.LEGS,
        EquipmentSlot.FEET,
    )

    /** @see PlayerPower */
    @JvmStatic
    fun of(player: PlayerEntity): Double {
        val armor = (player.armor / ARMOR_REFERENCE).coerceAtLeast(0.0)
        val health = ((player.maxHealth - 20.0f) / HEALTH_REFERENCE).coerceAtLeast(0.0)
        val attack = (
            player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE) / ATTACK_REFERENCE
            ).coerceAtLeast(0.0)
        val enchants = enchantmentLevels(player) / ENCHANT_LEVELS_REFERENCE
        return armor + health + attack + enchants
    }

    /**
     * Total enchantment levels on [player]'s hands and armour.
     *
     * Sums **levels**, not enchantment count: `Protection IV` on four pieces is `16` and should count for
     * more than four level-1 rolls. Vanilla's own mob-gear formula ignores enchantments entirely, which is
     * another way the modded player runs away from the curve.
     */
    private fun enchantmentLevels(player: PlayerEntity): Double {
        var total = 0
        for (slot in SLOTS) {
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
}
