package dev.firefly.simplemod.enchantments.legendary

import dev.firefly.simplemod.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantments
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