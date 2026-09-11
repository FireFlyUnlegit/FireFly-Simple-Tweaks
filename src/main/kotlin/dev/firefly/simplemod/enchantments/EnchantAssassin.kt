package dev.firefly.simplemod.enchantments

import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.EnumEnchantmentType
import net.minecraft.inventory.EntityEquipmentSlot
import net.minecraft.util.ResourceLocation

class EnchantAssassin private constructor() : Enchantment(
    Rarity.VERY_RARE,
    EnumEnchantmentType.WEAPON,
    arrayOf(EntityEquipmentSlot.MAINHAND)
){
    init {
        registryName = ResourceLocation("simplemod","assassin")
        setName("simplemod.assassin")
    }

    override fun getMaxLevel(): Int {
        return 5
    }

    override fun getMinEnchantability(enchantmentLevel: Int): Int {
        return 20 + 10 * enchantmentLevel
    }
    companion object {
        val INSTANCE = EnchantAssassin()
    }
}