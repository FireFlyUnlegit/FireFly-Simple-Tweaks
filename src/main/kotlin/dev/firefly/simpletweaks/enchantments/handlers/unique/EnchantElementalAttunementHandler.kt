package dev.firefly.simpletweaks.enchantments.handlers.unique

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.WorldSide
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.event.TickEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.*
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.elements.ElementType
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.component.type.ItemEnchantmentsComponent
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.item.ItemStack
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.enchantment.Enchantment
import java.util.concurrent.ThreadLocalRandom

/**
 * 元素重铸 — one-shot: replace the item's conversion element with **another random element**, keeping the
 * level.
 *
 * ## Why this is worth a UNIQUE slot
 * Conversions are mutually exclusive by tag, so a player who finds a fire conversion for a frost build has
 * exactly one vanilla answer: find another book. This turns the choice into a single consumable roll —
 * without adding any combat power, which is what this tier is for.
 *
 * ## Refusal cases (nothing consumed)
 * - the item has no conversion at all;
 * - the roll lands on the element it already had (excluded by construction).
 *
 * ⚠️ The result is random on purpose. A player who wants a *specific* element still has to trade; this is
 * the "get me out of this element" button, not a picker.
 */
@ModEnchantment(
    id = "elemental_attunement",
    category = EnchantCategory.UNIQUE,
    type = EnchantType.WEAPON,
    maxLevel = 1,
    weight = EnchantItemWeights.UNIQUE,
    anvilCost = 2,
    minCostBase = 1,
    minCostPerLevel = 1,
    supportedItems = EnchantSupportedItemTags.CONVERSION_WEAPONS,
    slots = [EnchantSlot.MAINHAND, EnchantSlot.OFFHAND],
)
object EnchantElementalAttunementHandler : Listenable {

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        val player = e.player
        if (WorldSide.isClient(player.world)) return

        val inv = player.inventory
        process(player, inv, inv.main)
        process(player, inv, inv.offHand)
    }

    private fun process(player: PlayerEntity, inv: PlayerInventory, list: List<ItemStack>) {
        for (i in list.indices) {
            val stack = list[i]
            if (stack.isEmpty) continue
            if (getItemSpecificEnchantLevel(stack, GeneratedEnchantments.ELEMENTAL_ATTUNEMENT) <= 0) continue

            var currentKey: RegistryKey<Enchantment>? = null
            var currentElement: ElementType? = null
            var level = 0
            for (element in ElementType.CONVERTIBLE) {
                val key = GeneratedEnchantments.keyOfElement(element.id, false) ?: continue
                val lvl = getItemSpecificEnchantLevel(stack, key)
                if (lvl > 0) {
                    currentKey = key
                    currentElement = element
                    level = lvl
                    break
                }
            }
            if (currentKey == null || currentElement == null) {
                FSTLog.log("Attunement") {
                    "item=${stack.item}, slot=$i, outcome=refused-no-conversion"
                }
                continue
            }

            val choices = ElementType.CONVERTIBLE.filter { it != currentElement }
            if (choices.isEmpty()) continue
            val pick = choices[ThreadLocalRandom.current().nextInt(choices.size)]
            val newKey = GeneratedEnchantments.keyOfElement(pick.id, false) ?: continue
            val registry = player.world.registryManager.get(RegistryKeys.ENCHANTMENT)
            val newEntry = registry.getEntry(newKey).orElse(null) ?: continue

            val builder = ItemEnchantmentsComponent.Builder(EnchantmentHelper.getEnchantments(stack))
            builder.remove { it.matchesKey(currentKey) }
            builder.remove { it.matchesKey(GeneratedEnchantments.ELEMENTAL_ATTUNEMENT) }
            builder.set(newEntry, level)
            EnchantmentHelper.set(stack, builder.build())
            // Mutation already landed in place (`EnchantmentHelper#set` writes through to the stack);
                // `PlayerInventory#setStack(i, ...)` takes a COMBINED index, so a list-local `i` wrote
                // the armour/offhand stack over hotbar slot i and duplicated it (`build=cleanup63`).

            FSTLog.log("Attunement") {
                "item=${stack.item}, slot=$i, from=${currentElement.id}, to=${pick.id}, level=$level, " +
                    "outcome=attuned"
            }
        }
    }
}
