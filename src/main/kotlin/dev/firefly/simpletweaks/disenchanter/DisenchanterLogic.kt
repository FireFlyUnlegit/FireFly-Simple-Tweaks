package dev.firefly.simpletweaks.disenchanter

import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.init.Items
import net.minecraft.item.ItemEnchantedBook
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound

object DisenchanterLogic {

    private const val EXP_PER_LEVEL = 12
    private const val EXP_PER_RARITY = 8
    private const val BOOK_BONUS = 30
    private const val TAG_KEY = "st_disenchant_exp"

    @JvmStatic
    fun calcExp(stack: ItemStack): Int {
        if (stack.isEmpty) return 0

        if (stack.item === Items.ENCHANTED_BOOK) {
            val list = ItemEnchantedBook.getEnchantments(stack)
            if (list.tagCount() == 0) return 0
            var exp = BOOK_BONUS
            for (i in 0 until list.tagCount()) {
                val tag = list.getCompoundTagAt(i)
                val id = tag.getShort("id").toInt()
                val lvl = tag.getShort("lvl").toInt()
                val ench = Enchantment.getEnchantmentByID(id) ?: continue
                exp += lvl * EXP_PER_LEVEL
                exp += ench.rarity.weight * EXP_PER_RARITY
            }
            return exp
        }

        if (!stack.isItemEnchanted) return 0
        val enchants = EnchantmentHelper.getEnchantments(stack)
        if (enchants.isEmpty()) return 0
        var exp = 0
        for ((ench, lvl) in enchants) {
            if (ench == null) continue
            exp += lvl * EXP_PER_LEVEL
            exp += ench.rarity.weight * EXP_PER_RARITY
        }
        return exp
    }

    @JvmStatic
    fun createDisenchanted(stack: ItemStack): ItemStack {
        if (stack.isEmpty) return ItemStack.EMPTY

        if (stack.item === Items.ENCHANTED_BOOK) {
            if (ItemEnchantedBook.getEnchantments(stack).tagCount() == 0) return ItemStack.EMPTY
            return ItemStack(Items.BOOK)
        }

        if (!stack.isItemEnchanted) return ItemStack.EMPTY
        val copy = stack.copy()
        copy.tagCompound?.removeTag("ench")

        copy.tagCompound?.let { tag ->
            if (tag.hasKey("display", 10)) {
                val display = tag.getCompoundTag("display")
                display.removeTag("Name")
                if (display.isEmpty) tag.removeTag("display")
            }
            if (tag.isEmpty) copy.tagCompound = null
        }
        return copy
    }

    @JvmStatic
    fun writeExpTag(stack: ItemStack, exp: Int) {
        if (exp <= 0) return
        val tag = stack.tagCompound ?: NBTTagCompound().also { stack.tagCompound = it }
        tag.setInteger(TAG_KEY, exp)
    }

    @JvmStatic
    fun readExpTag(stack: ItemStack): Int {
        val tag = stack.tagCompound ?: return 0
        return if (tag.hasKey(TAG_KEY, 99)) tag.getInteger(TAG_KEY) else 0
    }

    @JvmStatic
    fun clearExpTag(stack: ItemStack) {
        stack.tagCompound?.removeTag(TAG_KEY)
    }
}