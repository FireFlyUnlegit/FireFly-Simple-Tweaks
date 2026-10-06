package dev.firefly.simpletweaks.enchantments.mystery

import dev.firefly.simpletweaks.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantments
import net.minecraft.entity.EnumCreatureAttribute

object EnchantHeavenlyPunishment : ModEnchantments(
    "heavenly_punishment",
    ModEnchantmentType.SWORD,
    4,
    { it * 30 },
    EnchantmentCategories.MYSTERY,
) {
    override fun itemExtraDamage(level: Int, creatureType: EnumCreatureAttribute): Number {
        return 5.0 * level
    }
}