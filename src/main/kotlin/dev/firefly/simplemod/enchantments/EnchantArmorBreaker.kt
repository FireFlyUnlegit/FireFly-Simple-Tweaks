package dev.firefly.simplemod.enchantments

import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.EnumEnchantmentType
import net.minecraft.inventory.EntityEquipmentSlot
import net.minecraft.util.ResourceLocation

class EnchantArmorBreaker private constructor() : Enchantment(
    Rarity.VERY_RARE,
    EnumEnchantmentType.WEAPON,
    arrayOf(EntityEquipmentSlot.MAINHAND)
) {
    override fun getMaxLevel(): Int = 3
    override fun getMinEnchantability(enchantmentLevel: Int): Int = 16 + 6 * enchantmentLevel

    init {
        registryName = ResourceLocation("simplemod","armor_breaker")
        setName("simplemod.armor_breaker")
    }
    companion object {
        val INSTANCE = EnchantArmorBreaker()
    }
}