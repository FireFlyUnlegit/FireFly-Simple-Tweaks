package dev.firefly.simpletweaks.enchantments.handlers.unique

import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.unique.EnchantReForge
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.init.Items
import net.minecraft.inventory.IInventory
import net.minecraft.item.ItemStack
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent

object EnchantReForgeHandler : Listenable {

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        val p = e.player
        if (p.world.isRemote) return


        val inv = p.inventory
        processInventory(inv, inv.mainInventory)
        processInventory(inv, inv.armorInventory)
        processInventory(inv, inv.offHandInventory)
    }

    private fun processInventory(inv: IInventory, list: List<ItemStack>) {
        for (i in list.indices) {
            val stack = list[i]
            if (stack.isEmpty) continue
            if (stack.item == Items.ENCHANTED_BOOK) continue
            if (getItemSpecificEnchantLevel(stack, EnchantReForge) <= 0) continue

            val enchants = EnchantmentHelper.getEnchantments(stack)
            enchants.remove(EnchantReForge)
            EnchantmentHelper.setEnchantments(enchants, stack)

            val tag = stack.tagCompound ?: continue
            tag.removeTag("RepairCost")
            if (tag.isEmpty) stack.tagCompound = null

            inv.setInventorySlotContents(i, stack)
        }
    }
}