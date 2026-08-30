package dev.firefly.simplemod.enchantments

import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.EnumEnchantmentType
import net.minecraft.inventory.EntityEquipmentSlot
import net.minecraft.util.ResourceLocation
import net.minecraft.util.text.TextFormatting

class EnchantHealingBlade private constructor() : Enchantment(
    Rarity.VERY_RARE,
    EnumEnchantmentType.WEAPON,
    arrayOf(EntityEquipmentSlot.MAINHAND)
) {
    init {
        registryName = ResourceLocation("simplemod", "healing_blade")
        setName("simplemod.healing_blade")
    }

    override fun getMaxLevel(): Int = 8
    override fun getMinEnchantability(enchantmentLevel: Int): Int = 30 + 10 * (enchantmentLevel - 1)
    override fun isTreasureEnchantment() = true
    override fun getTranslatedName(level: Int): String {
        return "${TextFormatting.YELLOW}${super.getTranslatedName(level)}"
    }
    companion object {
        val INSTANCE = EnchantHealingBlade()
    }
}