package dev.firefly.simplemod.enchantments.mythic

import dev.firefly.simplemod.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantments
import net.minecraft.enchantment.Enchantment
import net.minecraft.entity.EnumCreatureAttribute
import net.minecraft.item.ItemStack
import net.minecraft.util.text.TextFormatting
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

object EnchantInfinitePower : ModEnchantments(
    "infinite_power",
    ModEnchantmentType.WEAPON,
    1,
    { Int.MAX_VALUE },
    EnchantmentCategories.MYTHIC,
) {

    override fun canApplyTogether(ench: Enchantment): Boolean = true
    override fun canApplyAtEnchantingTable(stack: ItemStack): Boolean = false
    override fun isTreasureEnchantment(): Boolean = true

    override fun itemExtraDamage(level: Int, creatureType: EnumCreatureAttribute): Number {
        return Int.MAX_VALUE
    }
    @SideOnly(Side.CLIENT)
    override fun decorateName(raw: String): String {
        return toRainbow(raw)
    }

    private fun toRainbow(text: String): String {
        val colors = listOf(
            TextFormatting.RED,
            TextFormatting.GOLD,
            TextFormatting.YELLOW,
            TextFormatting.GREEN,
            TextFormatting.AQUA,
            TextFormatting.BLUE,
            TextFormatting.LIGHT_PURPLE,
            TextFormatting.DARK_PURPLE
        )
        val sb = StringBuilder()
        val tick = colorTick / 20f
        for (i in text.indices) {
            val idx = ((tick + i * 0.08f) * colors.size).toInt() % colors.size
            sb.append(colors[idx]).append(text[i])
        }
        return sb.toString()
    }

    private var colorTick = 0

    fun tick() {
        colorTick = (colorTick + 1) % 60
    }
}