package dev.firefly.simplemod.enchantments

import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.EnumEnchantmentType
import net.minecraft.init.Enchantments
import net.minecraft.inventory.EntityEquipmentSlot
import net.minecraft.util.ResourceLocation

class EnchantItemFixer private constructor() : Enchantment(
    Rarity.VERY_RARE,
    EnumEnchantmentType.BREAKABLE,arrayOf(
    EntityEquipmentSlot.MAINHAND, EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS,
    EntityEquipmentSlot.FEET
)){
    init {
        registryName = ResourceLocation("simplemod","item_fixer")
        setName("simplemod.item_fixer")
    }

    override fun getMaxLevel(): Int = 8
    override fun canApplyTogether(ench: Enchantment): Boolean {
        return super.canApplyTogether(ench) && ench != Enchantments.MENDING
    }

    override fun getMinEnchantability(enchantmentLevel: Int): Int = 15
    override fun getMaxEnchantability(enchantmentLevel: Int): Int = 30

    override fun isTreasureEnchantment(): Boolean = false

    companion object {
        val INSTANCE = EnchantItemFixer()
    }
}