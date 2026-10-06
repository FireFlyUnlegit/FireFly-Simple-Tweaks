package dev.firefly.simpletweaks.enchantments.handlers.unique

import dev.firefly.simpletweaks.compat.STLog
import dev.firefly.simpletweaks.compat.WorldSide
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.event.TickEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.ModEnchantmentKeys
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.ItemEnchantmentsComponent
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items

/**
 * 1.21 port of `enchantments/handlers/unique/EnchantReForgeHandler.kt`.
 *
 * Line-by-line mapping from 1.12.2:
 * | 1.12.2                                                    | 1.21.1                                                                 |
 * |-----------------------------------------------------------|------------------------------------------------------------------------|
 * | `net.minecraftforge...TickEvent`                          | `compat.event.TickEvent`                                               |
 * | `net.minecraftforge...SubscribeEvent`                     | `compat.event.SubscribeEvent`                                          |
 * | `p.world.isRemote`                                        | `WorldSide.isClient(p.world)` (field_9236 is a FIELD, see MIGRATION)   |
 * | `EnchantReForge` (Enchantment object)                     | `ModEnchantmentKeys.REFORGE` (RegistryKey; id is `reforge`, not `re_forge`) |
 * | `EnchantmentHelper.getEnchantments(stack)` (mutable map)  | `EnchantmentHelper.getEnchantments(stack)` -> immutable `ItemEnchantmentsComponent` |
 * | `enchants.remove(EnchantReForge)` + `setEnchantments(...)`| `ItemEnchantmentsComponent.Builder(enchants).remove { it.matchesKey(REFORGE) }` + `EnchantmentHelper.set(stack, ...)` |
 * | `stack.tagCompound.removeTag("RepairCost")` / `tag.isEmpty`| `stack.remove(DataComponentTypes.REPAIR_COST)`                        |
 * | `Items.ENCHANTED_BOOK`                                    | same constant                                                          |
 * | `inv.mainInventory` / `armorInventory` / `offHandInventory`| `PlayerInventory.main` / `.armor` / `.offHand` (`field_7547`/`field_7548`/`field_7544`) |
 * | `IInventory.setInventorySlotContents(i, stack)`           | `PlayerInventory.setStack(i, stack)`                                    |
 *
 * ⚠️ Two forced mappings, both inherited project-wide:
 *  1. **`EnchantmentHelper.setEnchantments` does not exist in 1.21** and `getEnchantments` no longer
 *     returns a mutable `Map<Enchantment, Integer>`. The exact same edit ("drop this one enchantment,
 *     keep every other level") is expressed by rebuilding the component with
 *     [ItemEnchantmentsComponent.Builder], the identical pattern `EnchantDeathProtectionHandler` and
 *     `EnchantVoidProtectionHandler` already use.
 *  2. **`RepairCost` is not a raw NBT tag any more.** 1.21 stores it in the data component
 *     `minecraft:repair_cost` ([DataComponentTypes].REPAIR_COST) and `ItemStack.setNbt`/`getNbt` are
 *     gone; removing that component is what "clear the anvil penalty" means now. The 1.12.2
 *     `if (tag.isEmpty) stack.tagCompound = null` branch is *not* reproduced — it existed only
 *     because the old code held the whole compound in hand; removing exactly one component leaves no
 *     empty-compound equivalent to clean up.
 *
 * The 1.12.2 guard `if (stack.item == Items.ENCHANTED_BOOK) continue` is preserved verbatim: an
 * enchanted book carries the `reforge` enchantment but must not have it stripped.
 */
object EnchantReForgeHandler : Listenable {

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        val p = e.player
        if (WorldSide.isClient(p.world)) return


        val inv = p.inventory
        processInventory(inv, inv.main)
        processInventory(inv, inv.armor)
        processInventory(inv, inv.offHand)
    }

    /**
     * 1.12.2 passed `(IInventory inv, List<ItemStack> list)` and wrote back through the *inventory*
     * (`inv.setInventorySlotContents`); 1.21's `PlayerInventory` is itself the `Inventory`, so the
     * `IInventory` parameter collapses into the receiver and every list is addressed with the very
     * same index `setStack` would have used.
     */
    private fun processInventory(inv: PlayerInventory, list: List<ItemStack>) {
        for (i in list.indices) {
            val stack = list[i]
            if (stack.isEmpty) continue
            if (stack.item == Items.ENCHANTED_BOOK) continue
            if (getItemSpecificEnchantLevel(stack, ModEnchantmentKeys.REFORGE) <= 0) continue

            // 1.12.2: `val enchants = EnchantmentHelper.getEnchantments(stack)`;
            //          `enchants.remove(EnchantReForge)`; `EnchantmentHelper.setEnchantments(enchants, stack)`.
            // 1.21: the component is immutable, so rebuild it with the same one entry dropped.
            val enchants = EnchantmentHelper.getEnchantments(stack)
            val builder = ItemEnchantmentsComponent.Builder(enchants)
            builder.remove { it.matchesKey(ModEnchantmentKeys.REFORGE) }
            EnchantmentHelper.set(stack, builder.build())

            // 1.12.2: `val tag = stack.tagCompound ?: continue; tag.removeTag("RepairCost")`.
            // 1.21: the anvil penalty is the `minecraft:repair_cost` data component.
            stack.remove(DataComponentTypes.REPAIR_COST)

            inv.setStack(i, stack)
            STLog.log("ReForge") {
                "item=${stack.item}, slot=$i, repairCost=${stack.get(DataComponentTypes.REPAIR_COST)}, " +
                    "outcome=reforge-stripped"
            }
        }
    }
}
