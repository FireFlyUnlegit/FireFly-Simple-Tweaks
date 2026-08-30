package dev.firefly.simplemod.enchantments

import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.EnumEnchantmentType
import net.minecraft.inventory.EntityEquipmentSlot
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraftforge.event.RegistryEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

@Mod.EventBusSubscriber

class EnchantFlight private constructor() : Enchantment(
    Rarity.VERY_RARE,
    EnumEnchantmentType.ARMOR_CHEST,
    arrayOf(EntityEquipmentSlot.CHEST)
) {
    init {
        registryName = ResourceLocation("simplemod","flight")
        setName("simplemod.flight")
    }

    override fun getMaxLevel() = 3
    override fun isTreasureEnchantment() = true
    override fun canApplyAtEnchantingTable(stack: ItemStack) = true
    override fun getMinEnchantability(enchantmentLevel: Int) = 27 + 3 * enchantmentLevel
    companion object {
        val INSTANCE = EnchantFlight()
    }

}