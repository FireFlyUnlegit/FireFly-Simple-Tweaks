package dev.firefly.simpletweaks.enchantments.handlers.epic

import dev.firefly.simpletweaks.compat.ChargeBoost
import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.compat.event.ArrowLooseEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.item.ItemStack

@ModEnchantment(
    id = "fast_bow",
    category = EnchantCategory.EPIC,
    type = EnchantType.BOW,
//    color = EnchantCategory.EPIC.color,
    maxLevel = 5,
    weight = 5,
    anvilCost = 6,
    minCostBase = 20,
    minCostPerLevel = 10,
    supportedItems = "#minecraft:enchantable/bow",
    slots = [EnchantSlot.MAINHAND, EnchantSlot.OFFHAND],
)
object EnchantFastBowHandler : Listenable {



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
    /**
     * Level of `fast_bow` on [bow], `0` when absent.
     *
     * Split out of [chargeBoost] because a consumer can need the *level* rather than the multiplier
     * (multishot shortens its anti-tap gate by it), and keeping the component read in one place means
     * the two can never disagree about what the bow carries.
     */
    @JvmStatic
    fun fastBowLevel(bow: ItemStack): Int =
        getItemSpecificEnchantLevel(bow, GeneratedEnchantments.FAST_BOW)

    @JvmStatic
    fun chargeBoost(bow: ItemStack): Float {
        val level = fastBowLevel(bow)
        return if (level <= 0) 1.0f else 1.0f + 0.8f * level
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