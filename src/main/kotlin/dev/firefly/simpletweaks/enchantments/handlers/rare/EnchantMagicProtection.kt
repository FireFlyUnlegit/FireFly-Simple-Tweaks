package dev.firefly.simpletweaks.enchantments.handlers.rare

import dev.firefly.simpletweaks.compat.event.LivingDamageEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.compat.target
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantColor
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSupportedItemTags
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment
import dev.firefly.simpletweaks.enchantments.annotations.WEIGHT_FROM_CATEGORY
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.DamageTags
import dev.firefly.simpletweaks.util.getArmorEnchantLevel

@ModEnchantment(
    "magic_protection",
    EnchantCategory.RARE,
    EnchantType.ARMOR,
    EnchantColor.INHERIT,
    5,
    WEIGHT_FROM_CATEGORY,
    10,
    4,
    8,
    EnchantSupportedItemTags.ARMOR,
    slots = [EnchantSlot.ARMOR]
)
object EnchantMagicProtection : Listenable {

    @SubscribeEvent
    fun onLivingDamage(e: LivingDamageEvent) {
        if (e.invalid) return

        if (!e.source.isIn(DamageTags.MAGIC_DAMAGE)) return

        val lvl = e.target.getArmorEnchantLevel(GeneratedEnchantments.MAGIC_PROTECTION)
        if (lvl <= 0) return

        val reduction = (lvl * 0.04f).coerceAtMost(0.8f)
        e.amount *= (1.0f - reduction)
        if (e.amount < 0f) e.amount = 0f
    }
}