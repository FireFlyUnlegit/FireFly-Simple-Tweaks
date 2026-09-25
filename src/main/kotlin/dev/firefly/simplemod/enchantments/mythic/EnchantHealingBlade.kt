package dev.firefly.simplemod.enchantments.mythic

import dev.firefly.simplemod.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantments
import net.minecraft.entity.EnumCreatureAttribute

object EnchantHealingBlade : ModEnchantments(
    "healing_blade",
    ModEnchantmentType.WEAPON,
    8,
    {25 + 8 * it},
    category = EnchantmentCategories.MYTHIC
) {
    override fun itemExtraDamage(level: Int, creatureType: EnumCreatureAttribute): Number {
        return 1.0f * level
    }
}