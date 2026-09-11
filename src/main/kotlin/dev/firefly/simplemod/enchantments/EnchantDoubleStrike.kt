package dev.firefly.simplemod.enchantments

import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.EnumEnchantmentType
import net.minecraft.inventory.EntityEquipmentSlot
import net.minecraft.util.ResourceLocation

class EnchantDoubleStrike private constructor() : Enchantment(
    Rarity.VERY_RARE,
    EnumEnchantmentType.WEAPON,
    arrayOf(EntityEquipmentSlot.MAINHAND)
) {
    override fun getMaxLevel(): Int = 6
    override fun getMinEnchantability(enchantmentLevel: Int): Int = 29+ 4 * enchantmentLevel

    init {
        registryName = ResourceLocation("simplemod","double_strike")
        setName("simplemod.double_strike")
    }
    companion object {
        val INSTANCE = EnchantDoubleStrike()
    }
}