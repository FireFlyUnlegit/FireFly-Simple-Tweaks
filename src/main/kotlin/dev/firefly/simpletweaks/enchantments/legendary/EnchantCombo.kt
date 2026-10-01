package dev.firefly.simpletweaks.enchantments.legendary

import dev.firefly.simpletweaks.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantments
import net.minecraft.entity.EnumCreatureAttribute

object EnchantCombo : ModEnchantments(
    "combo",
    ModEnchantmentType.WEAPON,
    10,
    {15 + 5 * it},
    EnchantmentCategories.LEGENDARY
) {
    override fun itemExtraDamage(level: Int, creatureType: EnumCreatureAttribute): Number {
        return 0.15f * level
    }
}