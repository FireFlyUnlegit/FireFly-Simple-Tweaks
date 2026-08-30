package dev.firefly.simplemod.enchantments.enchantment_handlers.infinitepower

import net.minecraft.entity.player.EntityPlayer
import net.minecraft.inventory.IInventory
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagList
import net.minecraft.util.ResourceLocation
import net.minecraft.util.text.ITextComponent
import net.minecraft.util.text.TextComponentString
import net.minecraftforge.fml.common.registry.ForgeRegistries
import java.util.Locale

class InfiniteBagInventory(val player: EntityPlayer) : IInventory {
    private val items = ArrayList<ItemStack>()
    private val stackLimit = Int.MAX_VALUE
    private val MAX_ROWS = 54

    init {
        loadFromNBT()
        ensureCapacity(3)
    }

    private fun ensureCapacity(rows: Int) {
        val target = rows * 9
        while (items.size < target) {
            items.add(ItemStack.EMPTY)
        }
    }

    override fun getSizeInventory(): Int = items.size
    override fun isEmpty(): Boolean = items.all { it.isEmpty }

    override fun getStackInSlot(index: Int): ItemStack {
        return if (index < items.size) items[index] else ItemStack.EMPTY
    }

    override fun decrStackSize(index: Int, count: Int): ItemStack {
        if (index >= items.size) return ItemStack.EMPTY
        val stack = items[index]
        if (stack.isEmpty) return ItemStack.EMPTY
        val result = if (stack.count <= count) {
            val full = stack.copy()
            items[index] = ItemStack.EMPTY
            full
        } else {
            stack.splitStack(count)
        }
        markDirty()
        return result
    }

    override fun removeStackFromSlot(index: Int): ItemStack {
        if (index >= items.size) return ItemStack.EMPTY
        val stack = items[index]
        if (stack.isEmpty) return ItemStack.EMPTY
        items[index] = ItemStack.EMPTY
        markDirty()
        return stack
    }

    override fun setInventorySlotContents(index: Int, stack: ItemStack) {
        if (index >= items.size) {
            val newSize = ((index / 9) + 1) * 9
            ensureCapacity(newSize / 9)
        }
        items[index] = if (stack.isEmpty) ItemStack.EMPTY else stack.copy()
        markDirty()
    }

    override fun getInventoryStackLimit(): Int = stackLimit
    override fun markDirty() { saveToNBT() }
    override fun isUsableByPlayer(player: EntityPlayer): Boolean = true
    override fun openInventory(player: EntityPlayer) {}
    override fun closeInventory(player: EntityPlayer) {}
    override fun isItemValidForSlot(index: Int, stack: ItemStack): Boolean = true
    override fun getField(id: Int): Int = 0
    override fun setField(id: Int, value: Int) {}
    override fun getFieldCount(): Int = 0
    override fun clear() { items.clear(); markDirty() }
    override fun getName(): String = "Infinite Chest"
    override fun hasCustomName(): Boolean = false
    override fun getDisplayName(): ITextComponent = TextComponentString(getName())

    private fun saveToNBT() {
        val nbt = player.getEntityData()
        val list = NBTTagList()
        for (i in items.indices) {
            val stack = items[i]
            if (!stack.isEmpty) {
                val tag = NBTTagCompound()
                tag.setInteger("Slot", i)
                tag.setString("Id", stack.item.registryName.toString())
                tag.setInteger("Count", stack.count)
                tag.setInteger("Damage", stack.itemDamage)
                if (stack.hasTagCompound()) {
                    stack.tagCompound?.let { tag.setTag("tag", it.copy()) }
                }
                list.appendTag(tag)
            }
        }
        nbt.setTag("InfiniteBagItems", list)
        nbt.setInteger("InfiniteBagSize", items.size)
        player.getEntityData().setBoolean("__infinite_dirty", true)
    }

    private fun loadFromNBT() {
        val nbt = player.getEntityData()
        items.clear()
        if (nbt.hasKey("InfiniteBagItems")) {
            val list = nbt.getTagList("InfiniteBagItems", 10)
            val size = nbt.getInteger("InfiniteBagSize")
            while (items.size < size) items.add(ItemStack.EMPTY)
            for (i in 0 until list.tagCount()) {
                val tag = list.getCompoundTagAt(i)
                val slot = tag.getInteger("Slot")
                if (slot < items.size) {
                    val itemId = tag.getString("Id")
                    if (itemId.isNotEmpty()) {
                        val item = ForgeRegistries.ITEMS.getValue(ResourceLocation(itemId))
                        if (item != null) {
                            val stack = ItemStack(item, 1)
                            stack.count = tag.getInteger("Count")
                            stack.itemDamage = tag.getInteger("Damage")
                            if (tag.hasKey("tag")) {
                                stack.tagCompound = tag.getCompoundTag("tag").copy()
                            }
                            items[slot] = stack
                        } else {
                            items[slot] = ItemStack.EMPTY
                        }
                    } else {
                        items[slot] = ItemStack.EMPTY
                    }
                }
            }
        }
        if (items.isEmpty()) {
            for (i in 0..26) items.add(ItemStack.EMPTY)
        }
    }

    fun getItemDisplayName(index: Int): String {
        val stack = getStackInSlot(index)
        return if (stack.isEmpty) "" else stack.displayName.lowercase(Locale.ROOT)
    }
}