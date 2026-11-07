package dev.firefly.simpletweaks.enchantments.handlers.unique

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.WorldSide
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.event.TickEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.*
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.component.type.ItemEnchantmentsComponent
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.item.ItemStack

/**
 * 经验灌注 — one-shot: spend **10 experience levels** to raise this item's highest non-maxed enchantment by
 * one level.
 *
 * ## Why experience and not an item cost
 * Every other enchantment here adds a combat effect; this one converts a resource the player already has
 * into **progress on their gear**, which the mod could not do at all. The ceiling is the enchantment's own
 * `max_level` from the generated `MAX_LEVEL` table, so an illegal level is impossible by construction.
 *
 * ## Refusal cases (nothing is consumed)
 * - everything on the item is already maxed;
 * - the player has fewer than 10 levels.
 *
 * Both are logged: "nothing happened" and "a check refused" must be distinguishable (`AGENTS.md` §4.4).
 */
@ModEnchantment(
    id = "infusion",
    category = EnchantCategory.UNIQUE,
    type = EnchantType.BREAKABLE,
    maxLevel = 1,
    weight = EnchantItemWeights.UNIQUE,
    anvilCost = 2,
    minCostBase = 1,
    minCostPerLevel = 1,
    supportedItems = "#minecraft:enchantable/durability",
    slots = [EnchantSlot.ANY],
)
object EnchantInfusionHandler : Listenable {

    private const val COST = 10

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        val player = e.player
        if (WorldSide.isClient(player.world)) return

        val inv = player.inventory
        process(player, inv, inv.main)
        process(player, inv, inv.armor)
        process(player, inv, inv.offHand)
    }

    private fun process(player: PlayerEntity, inv: PlayerInventory, list: List<ItemStack>) {
        for (i in list.indices) {
            val stack = list[i]
            if (stack.isEmpty) continue
            if (getItemSpecificEnchantLevel(stack, GeneratedEnchantments.INFUSION) <= 0) continue

            val enchants = EnchantmentHelper.getEnchantments(stack)
            val upgradable = enchants.enchantments.filter { entry ->
                if (entry.matchesKey(GeneratedEnchantments.INFUSION)) return@filter false
                val id = entry.key.map { key -> key.value.path }.orElse(null) ?: return@filter false
                val max = GeneratedEnchantments.MAX_LEVEL[id] ?: return@filter false
                enchants.getLevel(entry) < max
            }
            if (upgradable.isEmpty()) {
                FSTLog.log("Infusion") { "item=${stack.item}, slot=$i, outcome=refused-all-maxed" }
                continue
            }
            if (player.experienceLevel < COST) {
                FSTLog.log("Infusion") {
                    "item=${stack.item}, slot=$i, levels=${player.experienceLevel}, outcome=refused-no-levels"
                }
                continue
            }

            val best = upgradable.maxByOrNull { enchants.getLevel(it) } ?: continue
            val next = enchants.getLevel(best) + 1

            val builder = ItemEnchantmentsComponent.Builder(enchants)
            builder.set(best, next)
            builder.remove { it.matchesKey(GeneratedEnchantments.INFUSION) }
            EnchantmentHelper.set(stack, builder.build())
            // Mutation already landed in place (`EnchantmentHelper#set` writes through to the stack);
                // `PlayerInventory#setStack(i, ...)` takes a COMBINED index, so a list-local `i` wrote
                // the armour/offhand stack over hotbar slot i and duplicated it (`build=cleanup63`).
            player.addExperienceLevels(-COST)

            FSTLog.log("Infusion") {
                "item=${stack.item}, slot=$i, level=${next}, levelsLeft=${player.experienceLevel}, " +
                    "outcome=infused"
            }
        }
    }
}
