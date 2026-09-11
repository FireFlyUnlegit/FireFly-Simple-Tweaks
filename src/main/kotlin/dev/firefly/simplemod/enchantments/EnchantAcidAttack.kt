package dev.firefly.simplemod.enchantments

import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.EnumEnchantmentType
import net.minecraft.inventory.EntityEquipmentSlot
import net.minecraft.util.ResourceLocation

class EnchantAcidAttack private constructor() : Enchantment(
    Rarity.VERY_RARE,
    EnumEnchantmentType.WEAPON,
    arrayOf(EntityEquipmentSlot.MAINHAND)
){
    init {
        registryName = ResourceLocation("simplemod","acid_attack")
        setName("simplemod.acid_attack")
    }

    override fun getMaxLevel(): Int {
        return 3
    }

    override fun getMinEnchantability(enchantmentLevel: Int): Int {
        return 10 + 15 * enchantmentLevel
    }

    companion object {
        val INSTANCE = EnchantAcidAttack()
    }


}