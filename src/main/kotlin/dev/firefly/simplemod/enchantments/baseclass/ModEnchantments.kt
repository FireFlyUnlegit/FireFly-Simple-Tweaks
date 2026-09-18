package dev.firefly.simplemod.enchantments.baseclass

import net.minecraft.enchantment.Enchantment
import net.minecraft.util.ResourceLocation
import net.minecraft.util.text.TextFormatting

abstract class ModEnchantments(
    id: String,
    modType: ModEnchantmentType,
    val enchantmentMaxLevel: Int,
    private val minAbility: (Int) -> Int,
    rarity: Rarity = Rarity.VERY_RARE,
    val textColor: TextFormatting = TextFormatting.GRAY,
) : Enchantment(rarity, modType.type, modType.slots) {

    init {
        registryName = ResourceLocation("simplemod", id)
        setName("simplemod.$id")
    }

    override fun getTranslatedName(level: Int): String {
        return if (textColor == TextFormatting.GRAY) super.getTranslatedName(level)
        else "$textColor${super.getTranslatedName(level)}"
    }
    override fun getMaxLevel(): Int {
        return enchantmentMaxLevel
    }

    override fun getMinEnchantability(enchantmentLevel: Int): Int =
        minAbility(enchantmentLevel)

}