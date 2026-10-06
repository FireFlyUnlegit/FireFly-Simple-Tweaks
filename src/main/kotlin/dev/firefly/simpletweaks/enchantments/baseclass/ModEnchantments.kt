package dev.firefly.simpletweaks.enchantments.baseclass

import dev.firefly.simpletweaks.core.config.GeneralConfig
import net.minecraft.client.resources.I18n
import net.minecraft.enchantment.Enchantment
import net.minecraft.entity.EnumCreatureAttribute
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraft.util.text.TextFormatting
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

abstract class ModEnchantments(
    id: String,
    val modType: ModEnchantmentType,
    val enchantmentMaxLevel: Int,
    private val minAbility: (Int) -> Int,
    val category: EnchantmentCategories = EnchantmentCategories.COMMON,
    val textColor: TextFormatting = category.color,
) : Enchantment(Rarity.VERY_RARE, modType.type, modType.slots) {

    init {
        registryName = ResourceLocation("simple_tweaks", id)
        setName("simple_tweaks.$id")
    }

    override fun canApply(stack: ItemStack): Boolean {
        return if (modType == ModEnchantmentType.WEAPON
            && stack.item is net.minecraft.item.ItemBow) {
            true
        } else super.canApply(stack)
    }
    override fun isTreasureEnchantment(): Boolean {
        return category.rarity >= 6
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
        return if (this.modType == ModEnchantmentType.SWORD) (0.2 + (category.rarity / 20.0)) * level else 0.0
    }
    override fun getMaxEnchantability(enchantmentLevel: Int): Int {
        return Int.MAX_VALUE
    }
}