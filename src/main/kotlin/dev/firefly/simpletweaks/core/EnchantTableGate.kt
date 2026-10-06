package dev.firefly.simpletweaks.core

import dev.firefly.simpletweaks.core.config.GeneralConfig
import net.minecraft.component.DataComponentTypes
import net.minecraft.item.ItemStack
import net.minecraft.item.Items

/**
 * The "can this stack go into the enchanting table?" predicate for B-2a, extracted so it can be
 * called from **both** the mixin and the dev-only `/sttest enchant` probe.
 *
 * ## Why this is a separate object rather than a private method in the mixin
 * The first attempt kept the predicate inline in `EnchantmentScreenHandlerMixin`, and the resulting
 * acceptance run could not distinguish these three very different failures:
 *  1. the `@Redirect` never ran (wrong call site),
 *  2. the redirect ran but the predicate returned false for the input,
 *  3. the redirect and predicate were both fine and something **downstream** (offer generation)
 *     produced zero power.
 * All three look identical from outside — `powers=[0,0,0]`. Hoisting the predicate out makes (2)
 * directly observable, and [invocations] makes (1) observable, because `/sttest enchant` can read
 * both. `vanillaCanEnchant` is kept as the reference so the probe can report what vanilla *would*
 * have said for the same stack.
 *
 * ## Fidelity note
 * The body is the 1.12.2 `MixinContainerEnchantment` predicate, in the original order:
 * `isEmpty -> false`, `ENCHANTED_BOOK -> true`, already enchanted `-> true`, else vanilla. It is
 * deliberately **not** behind a config flag, matching the original (that mixin had no config gate).
 */
object EnchantTableGate {

    /**
     * Diagnostic counter: how many times the mixin's redirect has consulted this predicate.
     *
     * Server-thread only, and only ever read by the dev command. Kept in production code because it
     * is one int increment on a path that runs at most a few times per GUI update, and because
     * deleting it would remove the only cheap way to answer "is the redirect live?" the next time
     * this seam regresses.
     */
    @JvmField
    var invocations: Int = 0

    /** The predicate the mixin applies. See the class KDoc for the diagnostic role of [invocations]. */
    @JvmStatic
    fun canEnchant(stack: ItemStack): Boolean {
        invocations++
        if (stack.isEmpty) {
            return false
        }
        if (stack.isOf(Items.ENCHANTED_BOOK)) {
            return true
        }
        val existing = stack.get(DataComponentTypes.ENCHANTMENTS)
        if (existing != null && !existing.isEmpty) {
            return true
        }
        return stack.isEnchantable()
    }

    /** What vanilla would have answered for the same stack — the reference value for the probe. */
    @JvmStatic
    fun vanillaCanEnchant(stack: ItemStack): Boolean = stack.isEnchantable()

    /**
     * The enchanting-table bookshelf cap, for the `@ModifyConstant` hook in `EnchantmentHelperMixin`.
     *
     * Port of 1.12.2 `MixinEnchantmentHelper#modifyMaxPower` + `MixinContainerEnchantment#firefly$forceEnchantability`'s
     * `maxPower` clamp: `disableEnchantmentTableLimit ? maxEnchantmentPower : 15`.
     *
     * <p>Lives here rather than in the Java mixin because that is where the config is idiomatic to
     * read, and because [canEnchant] already establishes the Kotlin-object-to-Java-mixin call pattern.
     *
     * <p>Note the default `maxEnchantmentPower` is **15**, i.e. equal to vanilla, so with a default
     * config this hook is a no-op by design — it only takes effect once the user raises the value.
     * `coerceAtLeast(1)` is re-applied here as well as in the config loader so a hand-edited 0 cannot
     * produce a table where every offer is level 0.
     */
    @JvmStatic
    fun maxEnchantmentPower(): Int =
        if (GeneralConfig.disableEnchantmentTableLimit) maxOf(1, GeneralConfig.maxEnchantmentPower) else 15

    /**
     * The stack's `minecraft:enchantments` contents, as `id:level;...` or `(none)`.
     *
     * Note this is **not** where an enchanted book's enchantments live — those are in
     * `minecraft:stored_enchantments`, which is exactly the distinction that made the enchanted-book
     * branch of this feature hard to diagnose.
     */
    @JvmStatic
    fun describeEnchantments(stack: ItemStack): String {
        val components = stack.get(DataComponentTypes.ENCHANTMENTS) ?: return "(none)"
        if (components.isEmpty) return "(none)"
        return components.enchantments.joinToString(";") { entry ->
            "${entry.key.map { it.value.toString() }.orElse("?")}:${components.getLevel(entry)}"
        }
    }

    /** The stack's `minecraft:stored_enchantments` contents, i.e. what an enchanted book carries. */
    @JvmStatic
    fun describeStoredEnchantments(stack: ItemStack): String {
        val components = stack.get(DataComponentTypes.STORED_ENCHANTMENTS) ?: return "(none)"
        if (components.isEmpty) return "(none)"
        return components.enchantments.joinToString(";") { entry ->
            "${entry.key.map { it.value.toString() }.orElse("?")}:${components.getLevel(entry)}"
        }
    }
}
