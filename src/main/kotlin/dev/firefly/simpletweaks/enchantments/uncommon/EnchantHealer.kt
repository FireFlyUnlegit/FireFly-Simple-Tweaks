package dev.firefly.simpletweaks.enchantments.uncommon

import dev.firefly.simpletweaks.enchantments.baseclass.EnchantmentCategories
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantmentType
import dev.firefly.simpletweaks.enchantments.baseclass.ModEnchantments
import net.minecraft.util.text.TextFormatting

object EnchantHealer : ModEnchantments(
    "healer",
    ModEnchantmentType.SWORD,
    5,
    {it * 11 + 20},
    textColor = TextFormatting.GREEN,
    category = EnchantmentCategories.UNCOMMON
)