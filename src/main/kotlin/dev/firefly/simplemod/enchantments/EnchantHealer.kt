package dev.firefly.simplemod.enchantments

import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.EnumEnchantmentType
import net.minecraft.inventory.EntityEquipmentSlot
import net.minecraft.util.ResourceLocation
import net.minecraft.util.text.TextFormatting

class EnchantHealer private constructor() : Enchantment(
    Rarity.VERY_RARE,
    EnumEnchantmentType.WEAPON,
    arrayOf(EntityEquipmentSlot.MAINHAND)
) {
    init {
        registryName = ResourceLocation("simplemod", "healer")
        setName("simplemod.healer")
    }

    override fun getMaxLevel(): Int = 5
    override fun getMinEnchantability(enchantmentLevel: Int): Int = 30 + 10 * (enchantmentLevel - 1)
    override fun isTreasureEnchantment() = false
    override fun getTranslatedName(level: Int): String {
        return "${TextFormatting.GREEN}${super.getTranslatedName(level)}"
    }
    companion object {
        val INSTANCE = EnchantHealer()
    }
}