package dev.firefly.simpletweaks.util

import net.minecraft.item.ItemStack

fun ItemStack.fix() {
    if (this.isItemDamaged) {
        this.itemDamage = 0
    }
}