package dev.firefly.simpletweaks.enchantments.handlers.common

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.event.BlockEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSupportedItemTags
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.registry.tag.BlockTags

/**
 * 丰收 — crops and leaves sometimes drop twice.
 *
 * ## Why `HarvestDropsEvent` and not a block-break hook
 * The seam already collects vanilla's generated drops into a mutable list and spawns whatever survives
 * (`BlockEvent.HarvestDropsEvent`, bridged from `Block#dropStacks`). Doubling there means **fortune and
 * silk-touch results are doubled too** — the bonus multiplies the block's real output instead of
 * adding a second, separately-rolled drop. That is also why it stacks with `fortune` rather than
 * replacing it.
 *
 * ## Why crops and leaves, not "everything a hoe can break"
 * The fantasy is harvest, not mining: a hoe on dirt does nothing for this, and restricting to the two
 * tags keeps it from becoming a second `tunneling`.
 *
 * ## Why the stacks are copied
 * `drops` is rebuilt by `AutoSmeltHandler` and read by the drop spawner afterwards; mutating the
 * `count` of the stack objects in place would work but would also silently change the item identity the
 * spawner later compares. Copies make this handler's effect local to the list.
 */
@ModEnchantment(
    id = "bountiful",
    category = EnchantCategory.COMMON,
    type = EnchantType.TOOL,
    maxLevel = 4,
    anvilCost = 3,
    minCostBase = 20,
    supportedItems = EnchantSupportedItemTags.HOE,
    slots = [EnchantSlot.MAINHAND],
)
object EnchantBountifulHandler : Listenable {

    /** Chance per level to double the whole drop list. IV = 60 %. */
    const val CHANCE_PER_LEVEL = 0.15f

    @SubscribeEvent
    fun onHarvestDrops(e: BlockEvent.HarvestDropsEvent) {
        val player = e.harvester ?: return
        if (e.drops.isEmpty()) return
        if (!e.state.isIn(BlockTags.CROPS) && !e.state.isIn(BlockTags.LEAVES)) return

        val level = getItemSpecificEnchantLevel(player.mainHandStack, GeneratedEnchantments.BOUNTIFUL)
        if (level <= 0) return

        val chance = (CHANCE_PER_LEVEL * level).coerceAtMost(1f)
        if (e.world.random.nextFloat() >= chance) return

        val doubled = e.drops.map {
            if (it.isEmpty) it else it.copy().also { copy -> copy.count = it.count * 2 }
        }
        e.drops.clear()
        e.drops.addAll(doubled)

        FSTLog.log("Bountiful") {
            "player=${player.name.string}, lvl=$level, block=${e.state.block}, " +
                "chance=$chance, stacks=${e.drops.size}, outcome=doubled"
        }
    }
}
