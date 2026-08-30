package dev.firefly.simplemod.enchantments

import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.EnumEnchantmentType
import net.minecraft.inventory.EntityEquipmentSlot
import net.minecraft.util.ResourceLocation

class EnchantBloodLust private constructor() : Enchantment(
    Rarity.VERY_RARE,
    EnumEnchantmentType.WEAPON,
    arrayOf(EntityEquipmentSlot.MAINHAND)
) {
    init {
        registryName= ResourceLocation("simplemod","bloodlust")
        setName("simplemod.bloodlust")
    }

    override fun getMaxLevel(): Int = 5
    override fun getMinEnchantability(enchantmentLevel: Int) = enchantmentLevel * 3 + 15
    companion object {
        val INSTANCE = EnchantBloodLust()
    }
}