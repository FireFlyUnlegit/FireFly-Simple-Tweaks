package dev.firefly.simplemod.enchantments

import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.EnumEnchantmentType
import net.minecraft.inventory.EntityEquipmentSlot
import net.minecraft.util.ResourceLocation
import net.minecraftforge.registries.IForgeRegistryEntry

class EnchantVoidProtection private constructor() : Enchantment(
    Rarity.VERY_RARE,
    EnumEnchantmentType.ARMOR_FEET,
    arrayOf(EntityEquipmentSlot.FEET)
) {
    init {
        registryName = ResourceLocation("simplemod","void_protection")
        setName("simplemod.void_protection")
    }

    override fun getMaxLevel(): Int = 3
    override fun getMinEnchantability(enchantmentLevel: Int) = enchantmentLevel * 10 + 20
    companion object {
        val INSTANCE = EnchantVoidProtection()
    }
}