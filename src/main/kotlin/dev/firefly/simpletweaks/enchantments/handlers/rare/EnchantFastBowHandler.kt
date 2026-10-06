package dev.firefly.simpletweaks.enchantments.handlers.rare

import dev.firefly.simpletweaks.compat.ChargeBoost
import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.compat.event.ArrowLooseEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.item.ItemStack

/**
 * `fast_bow`: the bow draws — and therefore reaches full power — sooner.
 *
 * **The first enchantment declared with `@ModEnchantment`**, i.e. the pilot for the KSP pipeline
 * (`MIGRATION.md` §15). `fast_bow` does **not** exist in the 1.12.2 mod, so nothing is being
 * reproduced and the numbers below are this port's own choice.
 *
 * ## The whole effect hangs off one number
 * A drawing bow's state is `LivingEntity#getItemUseTimeLeft()` — a plain field getter, verified, and
 * **not overridden by any subclass** (PlayerEntity, AbstractClientPlayerEntity, ClientPlayerEntity and
 * ServerPlayerEntity all inherit it). Everything derives from it:
 *
 * | consumer | reads | so scaling it changes |
 * |---|---|---|
 * | `HeldItemRenderer` (client) | `getItemUseTimeLeft()` at 7 call sites | **the drawn-back animation** |
 * | `LivingEntity#stopUsingItem` | passes `getItemUseTimeLeft()` as `remainingUseTicks` | the shot's power |
 * | `LivingEntity#getItemUseTime` | `getMaxUseTime - getItemUseTimeLeft()` | anything else that asks |
 *
 * ## Why there are two hooks, and why the client one checks its side
 * The effect is split so that **each side applies the multiplier exactly once**:
 * [dev.firefly.simpletweaks.mixin.LivingEntityDrawSpeedMixin] owns the client's animation and
 * [dev.firefly.simpletweaks.mixin.BowItemArrowLooseMixin] owns the server's shot power.
 *
 * They cannot both scale the same side: in singleplayer the client and the integrated server share
 * one JVM and one copy of the mixed class, so scaling the server path twice would compound to
 * `1.75²` — singleplayer would hit harder than multiplayer and harder than the animation shows. That
 * is why the client hook returns early unless `world.isClient()`.
 *
 * | level | multiplier | full power at |
 * |---|---|---|
 * | 1 | 1.25× | 16 ticks |
 * | 2 | 1.50× | 14 ticks |
 * | 3 | 1.75× | 12 ticks |
 *
 * Vanilla's `BowItem#getPullProgress` clamps to `1.0`, so none of this can overcharge a shot: the most
 * it does is make a partial draw count as a full one. The enchantment buys time, not damage.
 *
 * ## Why not the declarative route
 * 1.21 has no "draw faster" component, which is why `MIGRATION.md` §10.2 lists this kind of
 * enchantment as "handler required".
 */
@ModEnchantment(
    id = "fast_bow",
    category = "rare",
    type = "bow",
    color = "blue",
    maxLevel = 3,
    weight = 5,
    anvilCost = 6,
    minCostBase = 20,
    minCostPerLevel = 10,
    supportedItems = "#minecraft:enchantable/bow",
    slots = ["mainhand", "offhand"],
)
object EnchantFastBowHandler : Listenable {

    /** Draw-time multiplier added per level. Level 3 lands on 1.75x, i.e. full power at 12 ticks. */
    private const val BOOST_PER_LEVEL = 0.25f

    /**
     * Draw-speed multiplier for [bow]: `1.0` when the bow does not carry the enchantment.
     *
     * This is the **single source of truth** for the effect, which is why it lives on the handler
     * rather than inline in either mixin — the `@SubscribeEvent` below and the client-side
     * `LivingEntityDrawSpeedMixin` both call it, so the animation and the shot cannot drift apart.
     *
     * It reads the enchantment out of the stack's own component: no registry lookup, no world access,
     * so it is safe on the client while rendering **another** player's bow.
     */
    @JvmStatic
    fun chargeBoost(bow: ItemStack): Float {
        val level = getItemSpecificEnchantLevel(bow, GeneratedEnchantments.FAST_BOW)
        return if (level <= 0) 1.0f else 1.0f + BOOST_PER_LEVEL * level
    }

    /**
     * Server-side half: hands the multiplier to the `@ModifyArg` in `BowItemArrowLooseMixin`, which
     * scales the drawn-tick count vanilla feeds to `getPullProgress`.
     */
    @SubscribeEvent
    fun onArrowLoose(e: ArrowLooseEvent) {
        val boost = chargeBoost(e.bow)
        if (boost == 1.0f) return
        ChargeBoost.value = boost
        STLog.log("FastBow") { "boost=$boost, charge=${e.charge} (vanilla full draw = 20)" }
    }
}
