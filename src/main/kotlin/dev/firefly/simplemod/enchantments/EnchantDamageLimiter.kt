package dev.firefly.simplemod.enchantments

import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.EnumEnchantmentType
import net.minecraft.inventory.EntityEquipmentSlot
import net.minecraft.util.ResourceLocation

class EnchantDamageLimiter private constructor() : Enchantment(
    Rarity.VERY_RARE,
    EnumEnchantmentType.ARMOR,
    arrayOf(EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS,
        EntityEquipmentSlot.FEET)
){
    init {
        registryName = ResourceLocation("simplemod","damage_limiter")
        setName("simplemod.damage_limiter")
    }

    override fun getMaxLevel(): Int {
        return 3
    }

    override fun getMinEnchantability(enchantmentLevel: Int): Int {
        return 26 + 4 * enchantmentLevel
    }
    companion object {
        val INSTANCE = EnchantDamageLimiter()
    }
}