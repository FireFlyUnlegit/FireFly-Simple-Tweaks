package dev.firefly.simpletweaks.enchantments.handlers.unique

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.WorldSide
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.event.TickEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.*
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.ItemEnchantmentsComponent
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items

/**
 * 附魔剥离 — one-shot: take this item's **highest-level** enchantment off and turn it into a book of the
 * same level; the item keeps everything else.
 *
 * ## Why UNIQUE and not a power tier
 * This mod's UNIQUE tier is the "one-shot meta operation" tier (`reforge` was the only member): the
 * enchantment is consumed to perform an irreversible edit on an **item**. Moving an enchantment between
 * items is the biggest survival-mode gap this mod had — `/fst debugItem enchants copy` exists, but that is
 * an operator tool, not gameplay.
 *
 * ## Rules that keep it from being a dupe
 * - The enchantment is **moved**, never copied: removed from the item, written into the book.
 * - It **cannot extract itself** — that would let a single book duplicate forever.
 * - If the item carries nothing else, nothing happens and the enchantment is **not** consumed.
 *
 * ## Seam
 * `PlayerTickEvent`, exactly like `reforge`: 1.21 has no "anvil produced something" callback, and the tick
 * form is the established idiom in this tier.
 */
@ModEnchantment(
    id = "extraction",
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
object EnchantExtractionHandler : Listenable {

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
            if (getItemSpecificEnchantLevel(stack, GeneratedEnchantments.EXTRACTION) <= 0) continue

            val enchants = EnchantmentHelper.getEnchantments(stack)
            val candidates = enchants.enchantments.filter { !it.matchesKey(GeneratedEnchantments.EXTRACTION) }
            if (candidates.isEmpty()) continue

            val best = candidates.maxByOrNull { enchants.getLevel(it) } ?: continue
            val level = enchants.getLevel(best)

            val builder = ItemEnchantmentsComponent.Builder(enchants)
            builder.remove { it === best || it.matchesKey(GeneratedEnchantments.EXTRACTION) }
            EnchantmentHelper.set(stack, builder.build())
            inv.setStack(i, stack)

            val book = ItemStack(Items.ENCHANTED_BOOK)
            val stored = ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT)
            stored.add(best, level)
            book.set(DataComponentTypes.STORED_ENCHANTMENTS, stored.build())
            if (!player.inventory.insertStack(book)) player.dropItem(book, false)

            FSTLog.log("Extraction") {
                "item=${stack.item}, slot=$i, level=$level, outcome=extracted"
            }
        }
    }
}
