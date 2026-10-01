package dev.firefly.simpletweaks.util

import net.minecraft.client.Minecraft
import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.entity.EntityLivingBase
import net.minecraft.inventory.EntityEquipmentSlot
import net.minecraft.item.ItemStack

/**
 * 获取玩家主手物品上指定附魔的等级
 */
fun getSpecificEnchantLevel(enchantment: Enchantment): Int {
    val mc = Minecraft.getMinecraft()
    val player = mc.player ?: return 0
    val heldItem = player.heldItemMainhand ?: return 0
    return getItemSpecificEnchantLevel(heldItem, enchantment)
}

/**
 * 获取指定物品栈上指定附魔的等级
 */
fun getItemSpecificEnchantLevel(itemStack: ItemStack, enchantment: Enchantment): Int {
    return EnchantmentHelper.getEnchantmentLevel(enchantment, itemStack)
}
/**
 * 获取指定物品栈上指定附魔们的等级
 */
fun getItemSpecificEnchantsLevel(itemStack: ItemStack, enchantments: List<Enchantment>): Int {
    var total = 0
    enchantments.forEach { it -> total += getItemSpecificEnchantLevel(itemStack,it) }
    return total
}
/**
 * 获取指定物品们栈上指定附魔的等级
 */
fun getItemsSpecificEnchantLevel(itemStack: List<ItemStack>, enchantments: Enchantment): Int {
    var total = 0
    itemStack.forEach { it -> total += getItemSpecificEnchantLevel(it,enchantments) }
    return total
}
/**
 * 检查玩家主手物品是否有指定附魔
 */
fun hasEnchantment(enchantment: Enchantment): Boolean {
    return getSpecificEnchantLevel(enchantment) > 0
}

/**
 * 检查物品栈是否有指定附魔
 */
fun hasEnchantment(itemStack: ItemStack, enchantment: Enchantment): Boolean {
    return getItemSpecificEnchantLevel(itemStack, enchantment) > 0
}

/**
 * 获取实体所有盔甲上指定附魔的总等级
 */
fun EntityLivingBase.getArmorEnchantLevel(enchant: Enchantment): Int {
    var total = 0
    for (slot in EntityEquipmentSlot.entries) {
        if (slot.slotType != EntityEquipmentSlot.Type.ARMOR) continue
        total += EnchantmentHelper.getEnchantmentLevel(enchant, this.getItemStackFromSlot(slot))
    }
    return total
}