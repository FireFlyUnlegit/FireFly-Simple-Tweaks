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
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.tag.TagKey
import net.minecraft.util.Identifier
import java.util.concurrent.ThreadLocalRandom

/**
 * 词条洗练 — one-shot: **strip every enchantment this mod added** to the item and roll two fresh ones that
 * are legal for it.
 *
 * ## Why the generated tables were extended for this
 * Rolled enchantments must be applicable to the item, and the roll must not create a combination vanilla
 * would refuse (two conversions, for example). Both facts live in the `@ModEnchantment` annotation, so the
 * KSP processor now emits `SUPPORTED_TAGS` and `EXCLUSIVE_SETS` next to `MAX_LEVEL` — the alternative was
 * hand-copying 98 declarations into a runtime table, which would drift on the first new enchantment.
 *
 * ## Rules
 * - Only **this mod's** enchantments are stripped and only this mod's are rolled; vanilla enchantments on
 *   the item are untouched.
 * - Rolled level is random in `1 … ceil(maxLevel / 2)`, so a reforge can produce a good-but-not-perfect book
 *   — the gamble is the point, and the ceiling keeps it from being strictly better than hunting the real
 *   thing.
 * - If no candidate fits the item (a nearly impossible case, but possible for odd modded gear), nothing
 *   happens and the enchantment is **not** consumed.
 */
@ModEnchantment(
    id = "reroll",
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
object EnchantRerollHandler : Listenable {

    /** How many enchantments a single reroll produces. */
    private const val ROLL_COUNT = 2

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
            if (getItemSpecificEnchantLevel(stack, GeneratedEnchantments.REROLL) <= 0) continue

            val random = ThreadLocalRandom.current()
            val registry = player.world.registryManager.get(RegistryKeys.ENCHANTMENT)
            val chosen = ArrayList<String>(ROLL_COUNT)
            val usedSets = HashSet<String>(ROLL_COUNT)

            repeat(ROLL_COUNT) {
                val candidates = GeneratedEnchantments.IDS.filter { id ->
                    id != "reroll" && id !in chosen && appliesTo(stack, id) && !conflicts(id, usedSets)
                }
                if (candidates.isEmpty()) return@repeat
                val pick = candidates[random.nextInt(candidates.size)]
                chosen += pick
                GeneratedEnchantments.EXCLUSIVE_SETS[pick]?.let { usedSets += it }
            }

            if (chosen.isEmpty()) {
                FSTLog.log("Reroll") {
                    "item=${stack.item}, slot=$i, outcome=refused-no-candidates"
                }
                continue
            }

            // Keep vanilla enchantments, drop every mod one (including this one).
            val builder = ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT)
            for (entry in EnchantmentHelper.getEnchantments(stack).enchantments) {
                val id = entry.key.map { key -> key.value.path }.orElse(null)
                if (id != null && id in GeneratedEnchantments.IDS) continue
                builder.add(entry, EnchantmentHelper.getEnchantments(stack).getLevel(entry))
            }
            for (id in chosen) {
                val key = GeneratedEnchantments.KEYS.firstOrNull { it.value.path == id } ?: continue
                val entry = registry.getEntry(key).orElse(null) ?: continue
                val max = GeneratedEnchantments.MAX_LEVEL[id] ?: 1
                val level = 1 + random.nextInt(((max + 1) / 2).coerceAtLeast(1))
                builder.set(entry, level.coerceAtMost(max))
            }
            EnchantmentHelper.set(stack, builder.build())
            inv.setStack(i, stack)

            FSTLog.log("Reroll") {
                "item=${stack.item}, slot=$i, rolled=$chosen, outcome=rerolled"
            }
        }
    }

    /** Whether [id] may go on [stack], using the tag the declaration wrote. */
    private fun appliesTo(stack: ItemStack, id: String): Boolean {
        val raw = GeneratedEnchantments.SUPPORTED_TAGS[id] ?: return false
        val tagId = raw.removePrefix("#")
        val parsed = Identifier.tryParse(tagId) ?: return false
        return stack.isIn(TagKey.of(RegistryKeys.ITEM, parsed))
    }

    /** Whether [id] conflicts with an exclusive set already used by this roll. */
    private fun conflicts(id: String, usedSets: Set<String>): Boolean {
        val set = GeneratedEnchantments.EXCLUSIVE_SETS[id] ?: return false
        return set in usedSets
    }
}
