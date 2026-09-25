package dev.firefly.simplemod.enchantments.mystery

import dev.firefly.simplemod.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simplemod.enchantments.baseclass.ModEnchantments
import net.minecraft.entity.EnumCreatureAttribute

object EnchantCelestialBlessing : ModEnchantments(
    "celestial_blessing",
    ModEnchantmentType.WEAPON,
    5,
    {it * 30},
    EnchantmentCategories.MYSTERY,

) {
    override fun itemExtraDamage(level: Int, creatureType: EnumCreatureAttribute): Number {
        return 5.0 * level
    }
}