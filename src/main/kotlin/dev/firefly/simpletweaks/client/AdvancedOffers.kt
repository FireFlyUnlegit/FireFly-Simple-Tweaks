package dev.firefly.simpletweaks.client

import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments

/** Which enchantments the filling pool may hand out: the table's own pool only. */
object AdvancedOffers {
    private val BLOCKED = setOf(EnchantCategory.MYSTERY, EnchantCategory.UNIQUE)

    @JvmStatic
    fun allowed(path: String): Boolean {
        if (path == "infinite_power") return false
        return GeneratedEnchantments.CATEGORY[path]?.let { it !in BLOCKED } ?: false
    }
}