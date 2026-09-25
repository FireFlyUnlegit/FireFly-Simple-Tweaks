package dev.firefly.simplemod.enchantments.baseclass

import dev.firefly.simplemod.core.config.GeneralConfig
import net.minecraft.client.resources.I18n
import net.minecraft.enchantment.Enchantment
import net.minecraft.entity.EnumCreatureAttribute
import net.minecraft.util.ResourceLocation
import net.minecraft.util.text.TextFormatting
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

abstract class ModEnchantments(
    id: String,
    modType: ModEnchantmentType,
    val enchantmentMaxLevel: Int,
    private val minAbility: (Int) -> Int,
    val category: EnchantmentCategories = EnchantmentCategories.COMMON,
    val textColor: TextFormatting = category.color,
) : Enchantment(Rarity.VERY_RARE, modType.type, modType.slots) {

    init {
        registryName = ResourceLocation("simplemod", id)
        setName("simplemod.$id")
    }

    override fun isTreasureEnchantment(): Boolean {
        return category.rarity >= 5
    }
    override fun getMaxLevel(): Int = enchantmentMaxLevel

    override fun getMinEnchantability(enchantmentLevel: Int): Int =
        minAbility(enchantmentLevel)

    override fun getTranslatedName(level: Int): String {
        return decorateName(rawName(level))
    }

    @SideOnly(Side.CLIENT)
    protected fun rawName(level: Int): String {
        val base = I18n.format("enchantment.$name")
        return if (level == 1 && maxLevel == 1) base
        else "$base ${I18n.format("enchantment.level.$level")}"
    }

    protected open fun decorateName(raw: String): String {
        return if (!GeneralConfig.enabledEnchantmentColor) raw
        else "$textColor$raw"
    }

    override fun calcDamageByCreature(level: Int, creatureType: EnumCreatureAttribute): Float {
        return itemExtraDamage(level,creatureType).toFloat()
    }
    protected open fun itemExtraDamage(level: Int, creatureType: EnumCreatureAttribute): Number {
        return 0.0f
    }
}