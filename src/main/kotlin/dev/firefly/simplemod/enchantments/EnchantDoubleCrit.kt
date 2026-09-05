package dev.firefly.simplemod.enchantments

import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.EnumEnchantmentType
import net.minecraft.inventory.EntityEquipmentSlot
import net.minecraft.util.ResourceLocation

class EnchantDoubleCrit private constructor() : Enchantment(
    Rarity.VERY_RARE,
    EnumEnchantmentType.WEAPON,
    arrayOf(EntityEquipmentSlot.MAINHAND)
){
    init {
        registryName = ResourceLocation("simplemod","double_crit")
        setName("simplemod.double_crit")
    }

    override fun getMinEnchantability(enchantmentLevel: Int): Int = 30 * enchantmentLevel + 30
    override fun getMaxLevel(): Int = 5
    companion object {
        val INSTANCE = EnchantDoubleCrit()
    }
}