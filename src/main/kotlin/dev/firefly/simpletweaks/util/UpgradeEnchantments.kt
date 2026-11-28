package dev.firefly.simpletweaks.util

import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.ItemEnchantmentsComponent
import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.EnchantmentLevelEntry
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.entry.RegistryEntry

/**
 * The upgrade ladder: an "advanced" enchantment replaces its base at the base's **maximum** level and
 * then goes on growing on its own.
 *
 * The three rules that make this a ladder rather than a second enchantment:
 * 1. an upgrade may only arrive on an **enchanted book** used on an item that already holds the base
 *    enchantment at its maximum level — the enchanting table will not put it on an item directly;
 * 2. arriving consumes the base enchantment, so the item no longer carries it;
 * 3. once the upgrade is on an item, the base enchantment can no longer be added to it.
 *
 * For Arcane Echo's level sum an upgrade counts as `baseMax + ownLevel` (a single `greater_vitality I`
 * is eleven levels), which is what makes the ladder worth climbing there as well.
 */
object UpgradeEnchantments {

    /**
     * @param upgrade the advanced enchantment
     * @param base the enchantment it replaces
     * @param baseMaxLevel the base's maximum level, i.e. the level that has to be present to upgrade
     */
    data class Rule(
        val upgrade: RegistryKey<Enchantment>,
        val base: RegistryKey<Enchantment>,
        val baseMaxLevel: Int,
    )

    private val RULES: List<Rule> = listOf(
        Rule(GeneratedEnchantments.GREATER_VITALITY, GeneratedEnchantments.VITALITY, 10),
        Rule(GeneratedEnchantments.GREATER_ARMOR, GeneratedEnchantments.EXTRA_ARMOR, 8),
        Rule(GeneratedEnchantments.ADVANCED_DEFT, GeneratedEnchantments.DEFT, 3),
        Rule(GeneratedEnchantments.ADVANCED_FIRE_MASTER, GeneratedEnchantments.FIRE_MASTER, 6),
        Rule(GeneratedEnchantments.ADVANCED_FEATHER_FALL, GeneratedEnchantments.FEATHER_FALL, 4),
        Rule(GeneratedEnchantments.ADVANCED_SATURATION, GeneratedEnchantments.SATURATION, 8),
        Rule(GeneratedEnchantments.ADVANCED_FEATHER_FALL, GeneratedEnchantments.FEATHER_FALL, 4),
        Rule(GeneratedEnchantments.ADVANCED_BLOODLUST, GeneratedEnchantments.BLOODLUST, 5),
        Rule(GeneratedEnchantments.ADVANCED_TRUE_DAMAGE, GeneratedEnchantments.TRUE_DAMAGE, 7),
        Rule(GeneratedEnchantments.ADVANCED_FLEETFOOT, GeneratedEnchantments.FLEETFOOT, 3),
        Rule(GeneratedEnchantments.ADVANCED_HEALING_MULTIPLIER, GeneratedEnchantments.HEALING_MULTIPLIER, 10),
        Rule(GeneratedEnchantments.ADVANCED_ANTI_KNOCKBACK, GeneratedEnchantments.ANTI_KNOCKBACK, 2),
        Rule(GeneratedEnchantments.ADVANCED_FROSTBITE, GeneratedEnchantments.FROSTBITE, 3),
        Rule(GeneratedEnchantments.ADVANCED_GOLDEN_SHIELD, GeneratedEnchantments.GOLDEN_SHIELD, 3),
        Rule(GeneratedEnchantments.ADVANCED_IMMORTAL, GeneratedEnchantments.IMMORTAL, 1),
        Rule(GeneratedEnchantments.ADVANCED_SOUL_BOUND, GeneratedEnchantments.SOUL_BOUND, 1),
        Rule(GeneratedEnchantments.ADVANCED_SKYFALL, GeneratedEnchantments.SKYFALL, 3),
        Rule(GeneratedEnchantments.ADVANCED_SHIELD_BASH, GeneratedEnchantments.SHIELD_BASH, 3),
        Rule(GeneratedEnchantments.ADVANCED_SHOCKWAVE_LANDING, GeneratedEnchantments.SHOCKWAVE_LANDING, 3),
        Rule(GeneratedEnchantments.ADVANCED_DEVOUR, GeneratedEnchantments.DEVOUR, 3),
        Rule(GeneratedEnchantments.ADVANCED_KILL_AURA, GeneratedEnchantments.KILL_AURA, 6),
        Rule(GeneratedEnchantments.ADVANCED_ECHO_SHIELD, GeneratedEnchantments.ECHO_SHIELD, 5),
        Rule(GeneratedEnchantments.ADVANCED_STARFALL, GeneratedEnchantments.STARFALL, 5),
        Rule(GeneratedEnchantments.ADVANCED_HEALING_BLADE, GeneratedEnchantments.HEALING_BLADE, 8),
        Rule(GeneratedEnchantments.ADVANCED_HEAVENLY_PUNISHMENT, GeneratedEnchantments.HEAVENLY_PUNISHMENT, 4),
        Rule(GeneratedEnchantments.ADVANCED_MULTITUDE, GeneratedEnchantments.MULTITUDE, 2),
        Rule(GeneratedEnchantments.ADVANCED_KARMA, GeneratedEnchantments.KARMA, 4),
        Rule(GeneratedEnchantments.ADVANCED_RESONANCE_CORE, GeneratedEnchantments.RESONANCE_CORE, 3),
        Rule(GeneratedEnchantments.ADVANCED_MIDAS_TOUCH, GeneratedEnchantments.MIDAS_TOUCH, 3),
        Rule(GeneratedEnchantments.ADVANCED_AEGIS, GeneratedEnchantments.AEGIS, 3),
        Rule(GeneratedEnchantments.ADVANCED_BULWARK, GeneratedEnchantments.BULWARK, 3),
        Rule(GeneratedEnchantments.ADVANCED_EXECUTE, GeneratedEnchantments.EXECUTE, 5),
        Rule(GeneratedEnchantments.ADVANCED_FINALITY, GeneratedEnchantments.FINALITY, 3),
        Rule(GeneratedEnchantments.ADVANCED_REGENERATION, GeneratedEnchantments.REGENERATION, 8),
        Rule(GeneratedEnchantments.ADVANCED_VENOM, GeneratedEnchantments.VENOM, 3),
        Rule(GeneratedEnchantments.ADVANCED_BLOCKING, GeneratedEnchantments.BLOCKING, 1),
    )

    /** Level the base must be credited with when [component] carries [upgrade] in this slot, else 0. */
    @JvmStatic
    fun floorIn(component: net.minecraft.component.type.ItemEnchantmentsComponent, upgrade: RegistryKey<Enchantment>): Int {
        val rule = ruleOf(upgrade) ?: return 0
        return if (levelIn(component, upgrade) > 0) rule.baseMaxLevel else 0
    }

    @JvmStatic
    fun rules(): List<Rule> = RULES

    @JvmStatic
    fun ruleOf(upgrade: RegistryKey<Enchantment>): Rule? = RULES.firstOrNull { it.upgrade == upgrade }

    @JvmStatic
    fun isUpgrade(key: RegistryKey<Enchantment>): Boolean = RULES.any { it.upgrade == key }

    private val BASE_MAX: Map<RegistryKey<Enchantment>, Int> = RULES.associate { it.base to it.baseMaxLevel }
    private val UPGRADE_OF: Map<RegistryKey<Enchantment>, RegistryKey<Enchantment>> =
        RULES.associate { it.base to it.upgrade }

    /**
     * Max level of the base enchantment that [key] upgrades, or `0` when [key] has no upgrade step.
     *
     * An upgrade consumes its base enchantment, so the base effect must still be computed as if the
     * base were at this level — that is what `getItemSpecificEnchantLevel` floors it to.
     */
    @JvmStatic
    fun baseMaxOf(key: RegistryKey<Enchantment>): Int = BASE_MAX[key] ?: 0

    /** The upgrade step that replaces [key], or `null`. */
    @JvmStatic
    fun upgradeOf(key: RegistryKey<Enchantment>): RegistryKey<Enchantment>? = UPGRADE_OF[key]

    /** Enchantment levels this entry is worth in Arcane Echo's sum. */
    @JvmStatic
    fun echoLevel(key: RegistryKey<Enchantment>, level: Int): Int {
        val rule = ruleOf(key) ?: return level
        return rule.baseMaxLevel + level
    }

    /** Enchantment levels the given registry entry is worth in Arcane Echo's sum. */
    @JvmStatic
    fun echoLevelOf(entry: RegistryEntry<Enchantment>, level: Int): Int {
        val key = entry.key.orElse(null) ?: return level
        return echoLevel(key, level)
    }

    /** Level of [key] in one enchantment component, `0` when absent. */
    private fun levelIn(component: ItemEnchantmentsComponent?, key: RegistryKey<Enchantment>): Int {
        if (component == null) return 0
        for (entry in component.enchantments) {
            if (entry.matchesKey(key)) return component.getLevel(entry)
        }
        return 0
    }

    /**
     * Level of [key] on [stack], looking at both components: an enchanted book keeps its contents in
     * `stored_enchantments`, an enchanted item in `enchantments`.
     */
    private fun levelOf(stack: ItemStack, key: RegistryKey<Enchantment>): Int = maxOf(
        levelIn(stack.get(DataComponentTypes.ENCHANTMENTS), key),
        levelIn(stack.get(DataComponentTypes.STORED_ENCHANTMENTS), key),
    )

    /** True when the stack is a book (the advanced form is allowed on any book). */
    @JvmStatic
    fun isBook(stack: ItemStack): Boolean =
        stack.isOf(Items.BOOK) || stack.isOf(Items.ENCHANTED_BOOK)

    /** True when this stack already carries the base form at its own max level. */
    @JvmStatic
    fun baseAtMax(stack: ItemStack, rule: Rule): Boolean =
        levelOf(stack, rule.base) >= rule.baseMaxLevel

    /**
     * The level the upgrade ladder sees on [stack]: an upgrade reports `baseMax + level`, the base
     * enchantment reports its own level, and only the higher of the two counts (the base is stripped
     * when the upgrade lands, so this only matters for a half-migrated stack).
     */
    fun ladderLevel(stack: ItemStack, rule: Rule): Int {
        val base = levelOf(stack, rule.base)
        val upgrade = levelOf(stack, rule.upgrade)
        return if (upgrade > 0) maxOf(base, rule.baseMaxLevel + upgrade) else base
    }
    /**
     * The ladder level for [upgrade] on [stack], or `0` when that upgrade is not there.
     *
     * Handlers for an advanced enchantment must use this instead of the raw component level:
     * the upgrade means "a maxed base plus its own level", so `advanced_healing_blade III` has to
     * behave like `healing_blade XI`, not like `healing_blade III`.
     */
    @JvmStatic
    fun ladderLevelOf(stack: ItemStack, upgrade: RegistryKey<Enchantment>): Int {
        val rule = RULES.firstOrNull { it.upgrade == upgrade } ?: return 0
        return if (levelOf(stack, rule.upgrade) > 0) ladderLevel(stack, rule) else 0
    }

    /**
     * Enforces the anvil half of the ladder on the result vanilla just computed.
     *
     * [input] is the item in the left slot (what is being upgraded), [addition] the right slot (the
     * book or the second item). Mutates [result]'s enchantment component and returns whether anything
     * changed.
     */
    @JvmStatic
    fun sanitizeAnvilResult(result: ItemStack, input: ItemStack, addition: ItemStack): Boolean {
        if (result.isEmpty) return false
        // An enchanted book is how the ladder is delivered, never something the ladder gates.
        if (result.isOf(Items.ENCHANTED_BOOK)) return false
        val resultEnch = result.get(DataComponentTypes.ENCHANTMENTS) ?: return false

        var builder: ItemEnchantmentsComponent.Builder? = null
        fun edit(): ItemEnchantmentsComponent.Builder {
            val existing = builder ?: ItemEnchantmentsComponent.Builder(resultEnch).also { builder = it }
            return existing
        }

        for (rule in RULES) {
            val upgraded = levelIn(resultEnch, rule.upgrade)
            val baseInResult = levelIn(resultEnch, rule.base)
            val baseOnInput = levelOf(input, rule.base)
            val upgradeOnInput = levelOf(input, rule.upgrade)
            // The book slot keeps its contents in `stored_enchantments`, not `enchantments`.
            val upgradeFromAddition = levelOf(addition, rule.upgrade)

            if (upgraded > 0) {
                // Only a book may carry an upgrade onto an item, and only one that already sits at the
                // base's maximum level. Everything else is a vanilla merge we have to undo.
                // An item that already carries the upgrade keeps it when merged with anything at all:
                // the base was consumed the first time, so `baseOnInput` is 0 on every later trip.
                val legitimate = upgradeOnInput > 0 ||
                    (upgradeFromAddition > 0 && baseOnInput >= rule.baseMaxLevel)
                if (!legitimate) {
                    edit().remove { it.matchesKey(rule.upgrade) }
                } else if (baseInResult > 0) {
                    edit().remove { it.matchesKey(rule.base) }
                }
            } else if (baseInResult > 0 && upgradeOnInput > 0) {
                // The ladder already climbed: the base cannot be put back on this item.
                edit().remove { it.matchesKey(rule.base) }
            }
        }

        val edited = builder ?: return false
        result.set(DataComponentTypes.ENCHANTMENTS, edited.build())
        return true
    }

    /**
     * Drops upgrades from an enchanting-table offer when the item is not a book. The table may hand
     * out the book; it may not hand out an item that already carries the upgrade.
     */
    @JvmStatic
    fun filterTableOffers(offers: MutableList<EnchantmentLevelEntry>, stack: ItemStack): Boolean {
        if (stack.isOf(Items.BOOK) || stack.isOf(Items.ENCHANTED_BOOK)) return false
        return offers.removeIf { entry -> entry.enchantment.key.orElse(null)?.let(::isUpgrade) == true }
    }
}
