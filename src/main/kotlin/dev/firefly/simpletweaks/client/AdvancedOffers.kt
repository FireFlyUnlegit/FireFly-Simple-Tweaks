package dev.firefly.simpletweaks.client

import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments

/** Which enchantments the filling pool may hand out: the table's own pool only. */
object AdvancedOffers {
    private val BLOCKED = setOf(EnchantCategory.MYSTERY, EnchantCategory.UNIQUE)

    /**
     * An advanced form whose base cannot be rolled by the table must not be rolled by it either:
     * otherwise `advanced_mystery` books leak the mystery tier into a normal enchanting session.
     */
    @JvmStatic
    fun baseObtainable(basePath: String): Boolean {
        if (basePath == "infinite_power") return false
        return GeneratedEnchantments.CATEGORY[basePath]?.let { it !in BLOCKED } ?: false
    }

    @JvmStatic
    fun allowed(path: String): Boolean {
        if (path == "infinite_power") return false
        return GeneratedEnchantments.CATEGORY[path]?.let { it !in BLOCKED } ?: false
    }
}