package dev.firefly.simpletweaks.enchantments.mystery

import dev.firefly.simpletweaks.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantments
import net.minecraft.entity.EnumCreatureAttribute

object EnchantCelestialBlessing : ModEnchantments(
    "celestial_blessing",
    ModEnchantmentType.SWORD,
    5,
    {it * 30},
    EnchantmentCategories.MYSTERY,

) {
    override fun itemExtraDamage(level: Int, creatureType: EnumCreatureAttribute): Number {
        return 2.0 * level
    }

}