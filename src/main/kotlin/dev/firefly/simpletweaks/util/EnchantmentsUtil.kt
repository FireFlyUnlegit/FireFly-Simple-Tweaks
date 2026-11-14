package dev.firefly.simpletweaks.util

import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.LivingEntity
import net.minecraft.item.ItemStack
import net.minecraft.registry.RegistryKey
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments

/**
 * 1.21 port of `util/EnchantmentsUtil.kt`.
 *
 * The 1.12.2 version called `EnchantmentHelper.getEnchantmentLevel(Enchantment, ItemStack)`, which
 * no longer exists. `EnchantmentHelper.getLevel` in 1.21 takes a `RegistryEntry<Enchantment>`
 * (verified: method_8225, `(Ljm;Lcuq;)I`), and a bare `Enchantment` is no longer obtainable without
 * a registry lookup.
 *
 * Rather than resolve a `RegistryEntry` through the dynamic registry manager (which needs a world
 * and differs on client vs server), we look the entry up inside the stack's own enchantment
 * component and compare by [RegistryKey] with `RegistryEntry.matchesKey` (method_40225). This is
 * side-agnostic, allocation-light, and works for enchanted books too, because
 * `EnchantmentHelper.getEnchantments` internally picks STORED_ENCHANTMENTS for books.
 */

/** Level of [key] on [itemStack], or 0 if absent. */
fun getItemSpecificEnchantLevel(itemStack: ItemStack, key: RegistryKey<Enchantment>): Int {
    val component = EnchantmentHelper.getEnchantments(itemStack)
    if (component.isEmpty) return 0
    for (entry in component.enchantments) {
        if (entry.matchesKey(key)) return component.getLevel(entry)
    }
    return 0
}

/** Total level of every enchantment in [keys] present on [itemStack]. */
fun getItemSpecificEnchantsLevel(itemStack: ItemStack, keys: List<RegistryKey<Enchantment>>): Int =
    keys.sumOf { getItemSpecificEnchantLevel(itemStack, it) }

/** Total level of [key] across every stack in [itemStacks]. */
fun getItemsSpecificEnchantLevel(itemStacks: List<ItemStack>, key: RegistryKey<Enchantment>): Int =
    itemStacks.sumOf { getItemSpecificEnchantLevel(it, key) }

/**
 * Level of the element enchantment for [element] on [itemStack]: `<element>_conversion` when
 * [protective] is `false`, `<element>_protection` when it is `true`.
 *
 * Resolved **by id** through the generated table rather than by a 16-way `when`, so adding a ninth
 * element needs no edit here. Returns `0` when the generated table has no such id — the correct answer
 * for a missing enchantment, and also what a naming drift would degenerate to. It must not throw: a
 * damage handler calling this on the server thread would take the world down.
 */
fun getElementSpecificEnchantLevel(
    itemStack: ItemStack,
    element: dev.firefly.simpletweaks.util.elements.ElementType,
    protective: Boolean,
): Int {
    val key = GeneratedEnchantments.keyOfElement(element.id, protective) ?: return 0
    return getItemSpecificEnchantLevel(itemStack, key)
}

fun hasEnchantment(itemStack: ItemStack, key: RegistryKey<Enchantment>): Boolean =
    getItemSpecificEnchantLevel(itemStack, key) > 0

/**
 * Level of [key] on [LivingEntity]'s main hand.
 * Replaces the old `getSpecificEnchantLevel(enchantment)` which read the client player's held item.
 *
 * Served from [EnchantPresence] for our own enchantments, so repeated questions inside one hit do not
 * re-read the component; vanilla keys fall back to the stack.
 */
fun LivingEntity.getMainHandEnchantLevel(key: RegistryKey<Enchantment>): Int =
    if (EnchantPresence.tracks(key)) {
        EnchantPresence.mainHandLevelOf(this, key)
    } else {
        getItemSpecificEnchantLevel(this.mainHandStack, key)
    }

/**
 * Sum of [key] across all four armor slots, capped at [maxTotal].
 *
 * Served from [EnchantPresence] for our own enchantments; vanilla keys fall back to reading the four
 * stacks, because the snapshot only indexes the generated table.
 */
fun LivingEntity.getArmorEnchantLevel(
    key: RegistryKey<Enchantment>,
    maxTotal: Int = Int.MAX_VALUE
): Int {
    if (EnchantPresence.tracks(key)) {
        return EnchantPresence.armorLevelOf(this, key).coerceAtMost(maxTotal)
    }
    var total = 0
    for (slot in ARMOR_SLOTS) {
        total += getItemSpecificEnchantLevel(this.getEquippedStack(slot), key)
    }
    return total.coerceAtMost(maxTotal)
}

private val ARMOR_SLOTS = arrayOf(
    EquipmentSlot.HEAD,
    EquipmentSlot.CHEST,
    EquipmentSlot.LEGS,
    EquipmentSlot.FEET
)
