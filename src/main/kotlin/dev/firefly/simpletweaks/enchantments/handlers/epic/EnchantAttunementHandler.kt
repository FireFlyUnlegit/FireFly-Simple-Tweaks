package dev.firefly.simpletweaks.enchantments.handlers.epic

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.*
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.combatStack
import dev.firefly.simpletweaks.util.elements.ElementCombat
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.entity.player.PlayerEntity

/**
 * 同调 — `+2% × level` **final** damage for every distinct enchantment the weapon carries.
 *
 * ## Why "final" damage and a late priority
 * The point of the enchantment is to reward混搭 (many different lines) over stacking one line, so it has to
 * multiply the number the player actually sees — after crits, mastery, charged release and the rest. That
 * means running near the end of the pre-armour chain, hence `priority = 250` (above
 * `ElementCombat`'s 200, which must see the final amount for its conversion split).
 *
 * ## What counts as "one line"
 * Every enchantment on the weapon, **including vanilla ones** — Sharpness counts, our own count, and two
 * levels of the same enchantment count once. That is what makes the ceiling `~30 enchantments × 2% × level`
 * realistic only on a heavily anvil-worked blade, which is the intended payoff.
 *
 * ## Guard
 * Elemental riders (`isOwnElementalDamage`) are skipped: they are our own damage, and multiplying them would
 * let the enchantment feed itself through the reaction path.
 */
@ModEnchantment(
    id = "attunement",
    category = EnchantCategory.EPIC,
    type = EnchantType.SWORD,
    maxLevel = 3,
    anvilCost = 8,
    minCostBase = 24,
    supportedItems = EnchantSupportedItemTags.SWORD,
    slots = [EnchantSlot.MAINHAND, EnchantSlot.OFFHAND],
)
object EnchantAttunementHandler : Listenable {

    /** +2% per enchantment line per level. */
    private const val PER_LINE_PER_LEVEL = 0.02f

    @SubscribeEvent(priority = 250)
    fun onLivingHurt(e: LivingHurtEvent) {
        if (ElementCombat.isOwnElementalDamage(e)) return

        val attacker = e.source.attacker as? PlayerEntity ?: return
        val weapon = attacker.combatStack(e.source)
        if (weapon.isEmpty) return

        val level = getItemSpecificEnchantLevel(weapon, GeneratedEnchantments.ATTUNEMENT)
        if (level <= 0) return

        val lines = EnchantmentHelper.getEnchantments(weapon).enchantments.size
        if (lines <= 1) return // only itself: nothing to attune

        val multiplier = 1f + PER_LINE_PER_LEVEL * level * (lines - 1)
        val before = e.amount
        e.amount *= multiplier

        FSTLog.log("Attunement") {
            "weapon=${weapon.item}, level=$level, lines=$lines, multiplier=${"%.2f".format(multiplier)}, " +
                "damage=${"%.2f".format(before)}->${"%.2f".format(e.amount)}, outcome=attuned"
        }
    }
}
