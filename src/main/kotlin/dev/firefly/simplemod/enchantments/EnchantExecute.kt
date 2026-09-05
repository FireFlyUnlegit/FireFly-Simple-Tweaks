package dev.firefly.simplemod.enchantments

import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.EnumEnchantmentType
import net.minecraft.inventory.EntityEquipmentSlot
import net.minecraft.util.ResourceLocation

class EnchantExecute private constructor() : Enchantment(
    Rarity.VERY_RARE,
    EnumEnchantmentType.WEAPON,
    arrayOf(EntityEquipmentSlot.MAINHAND)
)
{
    init {
        registryName = ResourceLocation("simplemod","execute")
        setName("simplemod.execute")
    }

    override fun getMaxLevel(): Int = 5
    override fun getMinEnchantability(enchantmentLevel: Int): Int = enchantmentLevel * 5 + 20
    companion object {
        val INSTANCE = EnchantExecute()
    }
}
