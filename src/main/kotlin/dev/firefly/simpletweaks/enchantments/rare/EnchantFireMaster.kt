package dev.firefly.simpletweaks.enchantments.rare

import dev.firefly.simpletweaks.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantments
import net.minecraft.entity.EnumCreatureAttribute

object EnchantFireMaster : ModEnchantments(
    "fire_master",
    ModEnchantmentType.SWORD,
    6,
    {20 + 4*it},
    EnchantmentCategories.RARE,
) {
    override fun itemExtraDamage(level: Int, creatureType: EnumCreatureAttribute): Number {
        return level * 0.25
    }
}