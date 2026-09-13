package dev.firefly.simplemod.enchantments

import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.EnumEnchantmentType
import net.minecraft.inventory.EntityEquipmentSlot
import net.minecraft.util.ResourceLocation

class EnchantChargedStrike private constructor() : Enchantment(
    Rarity.VERY_RARE,
    EnumEnchantmentType.WEAPON,
    arrayOf(EntityEquipmentSlot.MAINHAND)
) {
    init {
        registryName = ResourceLocation("simplemod", "charged_strike")
        setName("simplemod.charged_strike")
    }

    override fun getMaxLevel(): Int = 5
    override fun getMinEnchantability(enchantmentLevel: Int): Int = 25 + 5 * enchantmentLevel

    companion object {
        val INSTANCE = EnchantChargedStrike()
    }
}