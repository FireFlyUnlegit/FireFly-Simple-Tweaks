package dev.firefly.simpletweaks.enchantments.handlers.common

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.event.BlockEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSupportedItemTags
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.tag.BlockTags

/**
 * 拾穗 — ore blocks occasionally drop one extra nugget-sized item.
 *
 * ## Why nuggets and not a copy of the ore
 * Copying the ore would be a second `fortune` (and would make a 1 % roll worth a whole diamond). A
 * nugget is the smallest unit the game already defines, so the bonus is noticeable on iron/gold without
 * being a jackpot: `iron_nugget` / `gold_nugget` for those two families, and `raw_copper` for copper
 * (1.21.1 has no copper nugget).
 *
 * ## Why the ore **tags**
 * `BlockTags.IRON_ORES` / `GOLD_ORES` / `COPPER_ORES` are what datapacks and other mods extend, so a
 * modded ore benefits without this file knowing about it. Matching block ids directly would silently
 * ignore every modded ore.
 *
 * ## Why the drop is added, not multiplied
 * The point is a *separate* small find ("拾穗" = gleaning), so it must not interact with fortune or with
 * `bountiful`'s doubling.
 */
@ModEnchantment(
    id = "gleaner",
    category = EnchantCategory.COMMON,
    type = EnchantType.TOOL,
    maxLevel = 4,
    anvilCost = 3,
    minCostBase = 20,
    supportedItems = EnchantSupportedItemTags.PICKAXE,
    slots = [EnchantSlot.MAINHAND],
)
object EnchantGleanerHandler : Listenable {

    /** Chance per level to glean one nugget. IV = 40 %. */
    const val CHANCE_PER_LEVEL = 0.10f

    @SubscribeEvent
    fun onHarvestDrops(e: BlockEvent.HarvestDropsEvent) {
        val player = e.harvester ?: return
        val tool = player.mainHandStack
        val level = getItemSpecificEnchantLevel(tool, GeneratedEnchantments.GLEANER)
        if (level <= 0) return

        val nugget = nuggetFor(e.state) ?: return
        val chance = (CHANCE_PER_LEVEL * level).coerceAtMost(1f)
        if (e.world.random.nextFloat() >= chance) return

        e.drops.add(ItemStack(nugget))

        FSTLog.log("Gleaner") {
            "player=${player.name.string}, lvl=$level, block=${e.state.block}, " +
                "nugget=${nugget}, chance=$chance, outcome=gleaned"
        }
    }

    /**
     * The nugget-sized item for an ore block, or `null` when this block is not one this enchantment
     * covers (copper gets `raw_copper`: 1.21.1 defines no `copper_nugget`).
     */
    private fun nuggetFor(state: net.minecraft.block.BlockState): net.minecraft.item.Item? = when {
        state.isIn(BlockTags.IRON_ORES) -> Items.IRON_NUGGET
        state.isIn(BlockTags.GOLD_ORES) -> Items.GOLD_NUGGET
        state.isIn(BlockTags.COPPER_ORES) -> Items.RAW_COPPER
        else -> null
    }
}
