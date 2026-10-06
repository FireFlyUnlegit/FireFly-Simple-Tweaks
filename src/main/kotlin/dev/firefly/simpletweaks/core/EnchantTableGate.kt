package dev.firefly.simpletweaks.core

import dev.firefly.simpletweaks.core.config.GeneralConfig
import net.minecraft.component.DataComponentTypes
import net.minecraft.item.ItemStack
import net.minecraft.item.Items

/**
 * The "can this stack go into the enchanting table?" predicate for B-2a, extracted so the redirect in
 * `EnchantmentScreenHandlerMixin` names its predicate instead of inlining it.
 *
 * ## Why this is a separate object rather than a private method in the mixin
 * The first attempt kept the predicate inline in `EnchantmentScreenHandlerMixin`, and the resulting
 * acceptance run could not distinguish these three very different failures:
 *  1. the `@Redirect` never ran (wrong call site),
 *  2. the redirect ran but the predicate returned false for the input,
 *  3. the redirect and predicate were both fine and something **downstream** (offer generation)
 *     produced zero power.
 * All three look identical from outside — `powers=[0,0,0]`. The dev-only `/sttest enchant` probe that
 * used to tell them apart has since been removed, but the predicate stays hoisted: it is the seam the
 * mixin calls, and keeping it named is what makes (2) inspectable at all.
 *
 * ## Fidelity note
 * The body is the 1.12.2 `MixinContainerEnchantment` predicate, in the original order:
 * `isEmpty -> false`, `ENCHANTED_BOOK -> true`, already enchanted `-> true`, else vanilla. It is
 * deliberately **not** behind a config flag, matching the original (that mixin had no config gate).
 */
object EnchantTableGate {

    /** The predicate the mixin applies. */
    @JvmStatic
    fun canEnchant(stack: ItemStack): Boolean {
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
}
