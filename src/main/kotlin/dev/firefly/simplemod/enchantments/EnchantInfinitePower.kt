package dev.firefly.simplemod.enchantments

import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.EnumEnchantmentType
import net.minecraft.inventory.EntityEquipmentSlot
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraft.util.text.TextFormatting
import net.minecraftforge.event.RegistryEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

@Mod.EventBusSubscriber
class EnchantInfinitePower private constructor() : Enchantment(
    Rarity.VERY_RARE,
    EnumEnchantmentType.WEAPON,
    arrayOf(EntityEquipmentSlot.MAINHAND, EntityEquipmentSlot.OFFHAND)
) {
    init {
        registryName = ResourceLocation("simplemod", "infinite_power")
        setName("simplemod.infinite_power")
    }

    override fun getMinEnchantability(level: Int): Int = Int.MAX_VALUE
    override fun getMaxEnchantability(level: Int): Int = Int.MAX_VALUE
    override fun getMaxLevel(): Int = 1
    override fun canApplyTogether(ench: Enchantment): Boolean = true
    override fun canApplyAtEnchantingTable(stack: ItemStack): Boolean = false
    override fun isTreasureEnchantment(): Boolean = true

    override fun getTranslatedName(level: Int): String {
        return toRainbow(super.getTranslatedName(level))
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
    companion object {
        val INSTANCE = EnchantInfinitePower()
        private var colorTick = 0

        fun tick() {
            colorTick = (colorTick + 1) % 60
        }
    }
}