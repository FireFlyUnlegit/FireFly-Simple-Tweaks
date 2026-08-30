package dev.firefly.simplemod.enchantments.enchantment_handlers.infinitepower

import net.minecraft.entity.player.EntityPlayer
import net.minecraft.inventory.Container
import net.minecraft.inventory.Slot
import net.minecraft.item.ItemStack

class ContainerInfiniteBag(player: EntityPlayer, val inventory: InfiniteBagInventory) : Container() {
    val TOTAL_SLOTS = 486

    init {
        for (i in 0 until TOTAL_SLOTS) {
            addSlotToContainer(object : Slot(inventory, i, 0, 0) {
                override fun getSlotStackLimit(): Int = Int.MAX_VALUE
            })
        }
        for (row in 0..2) {
            for (col in 0..8) {
                addSlotToContainer(Slot(player.inventory, col + row * 9 + 9, 8 + col * 18, 139 + row * 18))
            }
        }
        for (col in 0..8) {
            addSlotToContainer(Slot(player.inventory, col, 8 + col * 18, 197))
        }
    }

    override fun canInteractWith(playerIn: EntityPlayer): Boolean = true

    override fun mergeItemStack(stack: ItemStack, startIndex: Int, endIndex: Int, reverseDirection: Boolean): Boolean {
        var flag = false
        var i = if (reverseDirection) endIndex - 1 else startIndex

        if (stack.isStackable) {
            while (!stack.isEmpty) {
                if (if (reverseDirection) i < startIndex else i >= endIndex) break
                val slot = inventorySlots[i]
                val itemstack = slot.stack

                if (!itemstack.isEmpty && itemstack.item === stack.item &&
                    (!stack.hasSubtypes || stack.metadata == itemstack.metadata) &&
                    ItemStack.areItemStackTagsEqual(stack, itemstack)) {
                    val j = itemstack.count + stack.count
                    val maxSize = slot.slotStackLimit
                    if (j <= maxSize) {
                        stack.count = 0
                        itemstack.count = j
                        slot.onSlotChanged()
                        flag = true
                    } else if (itemstack.count < maxSize) {
                        stack.shrink(maxSize - itemstack.count)
                        itemstack.count = maxSize
                        slot.onSlotChanged()
                        flag = true
                    }
                }
                if (reverseDirection) --i else ++i
            }
        }

        if (!stack.isEmpty) {
            var i = if (reverseDirection) endIndex - 1 else startIndex
            while (true) {
                if (if (reverseDirection) i < startIndex else i >= endIndex) break
                val slot = inventorySlots[i]
                val itemstack = slot.stack
                if (itemstack.isEmpty && slot.isItemValid(stack)) {
                    val maxSize = slot.slotStackLimit
                    val newCount = if (stack.count > maxSize) stack.splitStack(maxSize) else stack.splitStack(stack.count)
                    slot.putStack(newCount)
                    slot.onSlotChanged()
                    flag = true
                    break
                }
                if (reverseDirection) --i else ++i
            }
        }
        return flag
    }

    override fun transferStackInSlot(playerIn: EntityPlayer, index: Int): ItemStack {
        var itemstack = ItemStack.EMPTY
        val slot = inventorySlots[index]
        if (slot != null && slot.hasStack) {
            val itemstack1 = slot.stack
            itemstack = itemstack1.copy()
            if (index < TOTAL_SLOTS) {
                if (!mergeItemStack(itemstack1, TOTAL_SLOTS, inventorySlots.size, true)) return ItemStack.EMPTY
            } else {
                if (!mergeItemStack(itemstack1, 0, TOTAL_SLOTS, false)) return ItemStack.EMPTY
            }
            if (itemstack1.isEmpty) slot.putStack(ItemStack.EMPTY)
            else slot.onSlotChanged()
        }
        return itemstack
    }

    fun getUsedRows(): Int {
        val size = inventory.getSizeInventory()
        return (size + 8) / 9
    }

    fun getMaxRows(): Int = 54
}