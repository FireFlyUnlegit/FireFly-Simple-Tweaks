package dev.firefly.simplemod.enchantments

import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.EnumEnchantmentType
import net.minecraft.inventory.EntityEquipmentSlot
import net.minecraft.util.ResourceLocation

class EnchantEffectBonus private constructor() : Enchantment(
    Rarity.VERY_RARE,
    EnumEnchantmentType.WEAPON,
    arrayOf(EntityEquipmentSlot.MAINHAND)
) {
    override fun getMaxLevel(): Int = 3
    override fun getMinEnchantability(enchantmentLevel: Int): Int = 16 + 6 * enchantmentLevel

    init {
        registryName = ResourceLocation("simplemod","effect_bonus")
        setName("simplemod.effect_bonus")
    }
    companion object {
        val INSTANCE = EnchantEffectBonus()
    }
}