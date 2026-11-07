package dev.firefly.simpletweaks.enchantments.handlers.common

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.WorldSide
import dev.firefly.simpletweaks.compat.event.BlockEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.enchantment.Enchantment
import net.minecraft.entity.EquipmentSlot
import net.minecraft.item.ItemStack
import net.minecraft.recipe.AbstractCookingRecipe
import net.minecraft.recipe.RecipeManager
import net.minecraft.recipe.RecipeType
import net.minecraft.recipe.input.SingleStackRecipeInput
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantItemWeights
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSupportedItemTags
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment

/**
 * 1.21 port of `enchantments/handlers/common/EnchantAutoSmeltHandler.kt`.
 *
 * Line-by-line mapping from 1.12.2:
 * | 1.12.2                                                   | 1.21.1                                                              |
 * |----------------------------------------------------------|---------------------------------------------------------------------|
 * | `net.minecraftforge.event.world.BlockEvent.HarvestDropsEvent` | `compat.event.BlockEvent.HarvestDropsEvent` (mixin seam, see below) |
 * | `e.harvester`                                            | `e.harvester` (same name)                                            |
 * | `e.drops`                                                | `e.drops` — `MutableList<ItemStack>`, same mutation style             |
 * | `EnchantAutoSmelt` (Enchantment object)                  | `GeneratedEnchantments.AUTO_SMELT` (RegistryKey)                        |
 * | `Enchantments.SILK_TOUCH`                                | `SILK_TOUCH` registry key built from `Identifier.ofVanilla`          |
 * | `FurnaceRecipes.instance().getSmeltingResult(drop)`      | `world.recipeManager.getFirstMatch(RecipeType.SMELTING, input, world)` |
 * | `FurnaceRecipes.instance().getSmeltingExperience(drop)`  | `AbstractCookingRecipe#getExperience()` (`method_8171`)              |
 * | `result.copy()` + `.count = drop.count * result.count`   | same, via `ItemStack#getCount`/`setCount`                            |
 * | `tool.damageItem(1, player)`                             | `tool.damage(1, player, EquipmentSlot.MAINHAND)`                     |
 * | `player.addExperience(exp)`                              | unchanged                                                           |
 * | `!player.world.isRemote`                                 | `WorldSide.isServer(player.world)`                                   |
 *
 * Two structural notes:
 *  - Everything is gated on a [ServerWorld] because 1.21 recipes live on the server world's recipe
 *    manager. Block drops are server-authoritative, so this loses nothing; the 1.12.2 client also
 *    had `FurnaceRecipes` available, which 1.21 does not.
 *  - The smelting XP *and* the 1-durability cost per smelted drop are both preserved — the reason
 *    this handler got a real mixin seam instead of a data-only `LootTableEvents.MODIFY` rewrite
 *    (a loot function is a pure item transform and could express neither).
 */
@ModEnchantment(
    id = "auto_smelt",
    category = EnchantCategory.COMMON,
    type = EnchantType.TOOL,
    maxLevel = 1,
    weight = EnchantItemWeights.COMMON,
    anvilCost = 2,
    minCostBase = 20,
    supportedItems = EnchantSupportedItemTags.MINING,
    slots = [EnchantSlot.MAINHAND],
    order = 2,
)
object EnchantAutoSmeltHandler : Listenable {

    /** 1.12.2 referenced the `Enchantments.SILK_TOUCH` object; 1.21 only exposes registry keys. */
    private val SILK_TOUCH: RegistryKey<Enchantment> =
        RegistryKey.of(RegistryKeys.ENCHANTMENT, Identifier.ofVanilla("silk_touch"))

    /**
     * The cooking recipe to use for [drop]: **blasting** if one exists, otherwise smelting.
     *
     * Blast-furnace recipes are the "faster" variant of a smelting recipe, so when both exist the
     * blast-furnace output is what the player asked for. Returns `null` when neither matches, which the
     * caller treats as "leave the drop untouched".
     */
    private fun firstRecipe(
        recipes: RecipeManager,
        drop: ItemStack,
        world: ServerWorld,
    ): AbstractCookingRecipe? {
        val input = SingleStackRecipeInput(drop)
        val blasting = recipes.getFirstMatch(RecipeType.BLASTING, input, world)
        if (blasting.isPresent) return blasting.get().value()
        val smelting = recipes.getFirstMatch(RecipeType.SMELTING, input, world)
        if (smelting.isPresent) return smelting.get().value()
        return null
    }

    @SubscribeEvent(priority = 50)
    fun onHarvestDrops(e: BlockEvent.HarvestDropsEvent) {
        val player = e.harvester ?: return
        val tool = player.mainHandStack
        if (getItemSpecificEnchantLevel(tool, GeneratedEnchantments.AUTO_SMELT) <= 0) return

        if (getItemSpecificEnchantLevel(tool, SILK_TOUCH) > 0) return

        val drops = e.drops
        if (drops.isEmpty()) return

        val world = e.world as? ServerWorld ?: return

        val recipes = world.recipeManager
        val newDrops = mutableListOf<ItemStack>()
        var totalExp = 0f
        var smeltedCount = 0

        for (drop in drops) {
            if (drop.isEmpty) continue
            // `build=cleanup25`: **blasting first, smelting as the fallback.**
            //
            // A blast furnace is a strictly better furnace for everything it supports, so a player with
            // this enchantment expects the blast-furnace product — and for a few items the two recipes
            // genuinely differ (a datapack may define both). The lookup is per-drop, so a mixed harvest
            // gets each item processed the way its own recipe set implies.
            val recipe = firstRecipe(recipes, drop, world)
            if (recipe != null) {
                smeltedCount++
                val smelted = recipe.craft(SingleStackRecipeInput(drop), world.registryManager)
                smelted.count = drop.count * smelted.count
                newDrops.add(smelted)
                totalExp += recipe.experience * drop.count
                tool.damage(1, player, EquipmentSlot.MAINHAND)
            } else {
                newDrops.add(drop)
            }
        }

        drops.clear()
        drops.addAll(newDrops)

        if (smeltedCount > 0) {
            FSTLog.log("AutoSmelt") {
                "player=${player.name.string}, lvl=${getItemSpecificEnchantLevel(tool, GeneratedEnchantments.AUTO_SMELT)}, " +
                    "smelted=$smeltedCount/${drops.size}, exp=$totalExp, toolDamage=${tool.damage}"
            }
        }

        if (totalExp > 0f && WorldSide.isServer(player.world)) {
            val exp = totalExp.toInt()
            if (exp > 0) player.addExperience(exp)
        }
    }
}
