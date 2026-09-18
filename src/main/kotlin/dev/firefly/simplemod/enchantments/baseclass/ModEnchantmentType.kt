package dev.firefly.simplemod.enchantments.baseclass

import net.minecraft.enchantment.EnumEnchantmentType
import net.minecraft.inventory.EntityEquipmentSlot

enum class ModEnchantmentType(
    val type: EnumEnchantmentType,
    val slots: Array<EntityEquipmentSlot>
) {
    WEAPON(
        EnumEnchantmentType.WEAPON,
        arrayOf(EntityEquipmentSlot.MAINHAND)
    ),
    HELD(
        EnumEnchantmentType.WEAPON,
        arrayOf(EntityEquipmentSlot.MAINHAND, EntityEquipmentSlot.OFFHAND)
    ),
    ARMOR(
        EnumEnchantmentType.ARMOR,
        arrayOf(
            EntityEquipmentSlot.HEAD,
            EntityEquipmentSlot.CHEST,
            EntityEquipmentSlot.LEGS,
            EntityEquipmentSlot.FEET
        )
    ),
    HELMET(
        EnumEnchantmentType.ARMOR_HEAD,
        arrayOf(EntityEquipmentSlot.HEAD)
    ),
    CHESTPLATE(
        EnumEnchantmentType.ARMOR_CHEST,
        arrayOf(EntityEquipmentSlot.CHEST)
    ),
    LEGGINGS(
        EnumEnchantmentType.ARMOR_LEGS,
        arrayOf(EntityEquipmentSlot.LEGS)
    ),
    BOOTS(
        EnumEnchantmentType.ARMOR_FEET,
        arrayOf(EntityEquipmentSlot.FEET)
    ),
    BREAKABLE(
        EnumEnchantmentType.BREAKABLE,
        arrayOf(
            EntityEquipmentSlot.MAINHAND,
            EntityEquipmentSlot.HEAD,
            EntityEquipmentSlot.CHEST,
            EntityEquipmentSlot.LEGS,
            EntityEquipmentSlot.FEET
        )
    ),
}