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
    )

    @JvmStatic
    fun rules(): List<Rule> = RULES

    @JvmStatic
    fun ruleOf(upgrade: RegistryKey<Enchantment>): Rule? = RULES.firstOrNull { it.upgrade == upgrade }

    @JvmStatic
    fun isUpgrade(key: RegistryKey<Enchantment>): Boolean = RULES.any { it.upgrade == key }

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
                val legitimate = upgradeFromAddition > 0 && baseOnInput >= rule.baseMaxLevel
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
