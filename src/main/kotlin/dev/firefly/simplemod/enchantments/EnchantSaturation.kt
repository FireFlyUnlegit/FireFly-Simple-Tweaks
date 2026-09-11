package dev.firefly.simplemod.enchantments

import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.EnumEnchantmentType
import net.minecraft.inventory.EntityEquipmentSlot
import net.minecraft.util.ResourceLocation

class EnchantSaturation private constructor() : Enchantment(
    Rarity.VERY_RARE,
    EnumEnchantmentType.ARMOR_CHEST,
    arrayOf(EntityEquipmentSlot.CHEST)
){
    init {
        registryName = ResourceLocation("simplemod","saturation")
        setName("simplemod.saturation")
    }

    override fun getMaxLevel(): Int {
        return 8
    }

    override fun getMinEnchantability(enchantmentLevel: Int): Int {
        return 23+3*enchantmentLevel
    }
    companion object {
        val INSTANCE = EnchantSaturation()
    }
}