package dev.firefly.simplemod.enchantments

import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.EnumEnchantmentType
import net.minecraft.inventory.EntityEquipmentSlot
import net.minecraft.util.ResourceLocation

class EnchantAoeAttack private constructor() : Enchantment(
    Rarity.VERY_RARE,
    EnumEnchantmentType.WEAPON,
    arrayOf(EntityEquipmentSlot.MAINHAND)
) {
    init {
        registryName = ResourceLocation("simplemod", "aoe_attack")
        setName("simplemod.aoe_attack")
    }

    override fun getMaxLevel(): Int = 5
    override fun getMinEnchantability(enchantmentLevel: Int): Int = 25 + 5 * enchantmentLevel

    companion object {
        val INSTANCE = EnchantAoeAttack()
    }
}