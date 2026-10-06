package dev.firefly.simpletweaks.enchantments.handlers.rare

import dev.firefly.simpletweaks.compat.ChargeBoost
import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.compat.event.ArrowLooseEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel

/**
 * `fast_bow`: the bow reaches full power sooner — **the first enchantment declared with
 * `@ModEnchantment`**, i.e. the pilot for the KSP pipeline (`MIGRATION.md` §15).
 *
 * ## Where the effect comes from
 * Vanilla's shot power is `BowItem.getPullProgress(ticksDrawn)`, which saturates at 20 drawn ticks
 * (`f = (f² + 2f)/3` reaches exactly `1.0` at `f = 1`). This handler hands a multiplier to that call
 * through [ChargeBoost], so a lower level of draw counts as a full one:
 *
 * | level | multiplier | full power at |
 * |---|---|---|
 * | 1 | 1.25× | 16 ticks |
 * | 2 | 1.50× | 14 ticks |
 * | 3 | 1.75× | 12 ticks |
 *
 * Because [net.minecraft.item.BowItem.getPullProgress] clamps to `1.0`, this **cannot** overcharge a
 * shot: the maximum is ordinary full-draw power. The enchantment buys time, not damage — which is
 * what "reduces bow draw time" on the tooltip promises.
 *
 * ## Why not the declarative route
 * 1.21 has no component for "draw faster". The formula lives in `getPullProgress` and is consumed
 * only by `onStoppedUsing`, so this is one of the enchantments the design-decision table in
 * `MIGRATION.md` §10.2 records as "handler required".
 *
 * ## Not ported from 1.12.2
 * `fast_bow` **does not exist in the 1.12.2 mod** — it is new here, so nothing is being reproduced
 * and the numbers above are this port's own choice.
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

    @SubscribeEvent
    fun onArrowLoose(e: ArrowLooseEvent) {
        val level = getItemSpecificEnchantLevel(e.bow, GeneratedEnchantments.FAST_BOW)
        if (level <= 0) return

        val boost = 1.0f + BOOST_PER_LEVEL * level
        ChargeBoost.value = boost
        STLog.log("FastBow") { "lvl=$level, charge=${e.charge}, boost=$boost" }
    }
}
