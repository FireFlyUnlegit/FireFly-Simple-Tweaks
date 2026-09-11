package dev.firefly.simplemod.enchantments

import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.EnumEnchantmentType
import net.minecraft.inventory.EntityEquipmentSlot
import net.minecraft.util.ResourceLocation

class EnchantImmortal private constructor() : Enchantment(
    Rarity.VERY_RARE,
    EnumEnchantmentType.ARMOR,
    arrayOf(EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS,
        EntityEquipmentSlot.FEET)
){
    init {
        registryName = ResourceLocation("simplemod","immortal")
        setName("simplemod.immortal")
    }

    override fun getMaxLevel(): Int {
        return 1
    }

    override fun getMinEnchantability(enchantmentLevel: Int): Int {
        return 30
    }
    companion object {
        val INSTANCE = EnchantImmortal()
    }
}