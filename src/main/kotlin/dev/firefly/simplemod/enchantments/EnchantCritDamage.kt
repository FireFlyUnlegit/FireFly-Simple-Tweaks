package dev.firefly.simplemod.enchantments

import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.EnumEnchantmentType
import net.minecraft.inventory.EntityEquipmentSlot
import net.minecraft.util.ResourceLocation

class EnchantCritDamage private constructor() : Enchantment(
    Rarity.VERY_RARE,
    EnumEnchantmentType.WEAPON,
    arrayOf(EntityEquipmentSlot.MAINHAND)
) {
    init {
        registryName= ResourceLocation("simplemod","crit_damage")
        setName("simplemod.crit_damage")
    }
    override fun getMinEnchantability(enchantmentLevel: Int): Int = 15 + 5 * enchantmentLevel
    override fun getMaxLevel(): Int = 5

    companion object {
        val INSTANCE = EnchantCritDamage()
    }
}