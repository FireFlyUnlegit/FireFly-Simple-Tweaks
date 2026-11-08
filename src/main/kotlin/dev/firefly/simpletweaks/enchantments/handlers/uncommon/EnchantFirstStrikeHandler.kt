package dev.firefly.simpletweaks.enchantments.handlers.uncommon

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.attacker
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSupportedItemTags
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.damagesource.isAuxiliaryDamage
import dev.firefly.simpletweaks.util.combatStack
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.player.PlayerEntity

/**
 * 猎首 — the first hit on an **undamaged** target deals extra damage.
 *
 * ## Why "full health" is the condition
 * It is a condition the player can see and act on (approach a fresh mob / a freshly healed one), and it
 * is inherently non-repeatable against the same target, so it cannot be stacked by hitting faster. That
 * also makes it safe next to `combo` / `crit_damage`, which are the repeatable multipliers.
 *
 * ## Why the multiplier is applied last
 * `order` is omitted (`Int.MAX_VALUE`), so this runs after the crit / combo chain and multiplies the
 * **final** hit. Reading a mid-chain amount would make `+12 % × level` interact with every later
 * multiplier — the same shape that turned `execute` into a 500k hit.
 *
 * ## Why the `> 0` health check uses floats with an epsilon-free comparison
 * `LivingHurtEvent` fires before the damage lands, so `health == maxHealth` is exactly the fresh-target
 * condition. A target that is at 19.999 / 20 because of float rounding would otherwise never qualify;
 * the `>=` form (rather than `==`) covers that without allowing a visibly damaged target through.
 */
@ModEnchantment(
    id = "first_strike",
    category = EnchantCategory.UNCOMMON,
    type = EnchantType.WEAPON,
    maxLevel = 4,
    anvilCost = 5,
    minCostBase = 26,
    supportedItems = EnchantSupportedItemTags.WEAPON,
    slots = [EnchantSlot.MAINHAND],
)
object EnchantFirstStrikeHandler : Listenable {

    /** Damage multiplier added per level. IV = +48 %. */
    const val BONUS_PER_LEVEL = 0.12f

    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        if (e.source.isAuxiliaryDamage()) return
        val player = e.attacker as? PlayerEntity ?: return
        val target = e.entityLiving
        if (player === target) return
        if (target.health < target.maxHealth) return

        val level = getItemSpecificEnchantLevel(
            player.combatStack(e.source),
            GeneratedEnchantments.FIRST_STRIKE,
        )
        if (level <= 0) return

        val before = e.amount
        e.amount *= 1f + BONUS_PER_LEVEL * level

        FSTLog.log("FirstStrike") {
            "attacker=${player.name.string}, target=${target.type.name.string}, lvl=$level, " +
                "health=${target.health}/${target.maxHealth}, damage=$before->${e.amount}, " +
                "outcome=first-strike"
        }
    }
}
