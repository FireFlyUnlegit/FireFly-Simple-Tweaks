package dev.firefly.simpletweaks.enchantments.handlers.common

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSupportedItemTags
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.damagesource.isAuxiliaryDamage
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.registry.tag.DamageTypeTags

/**
 * 羽落 — fall damage is reduced by a fraction per level.
 *
 * ## Why this is a damage multiplier and not a `LivingFallEvent` cancel
 * The `LivingFallEvent` seam exposes `distance` / `damageMultiplier` as **read-only** on purpose (see
 * that class's KDoc: mutating them needs two `@ModifyVariable` injectors on adjacent `float`
 * parameters). Vanilla routes fall damage through `LivingEntity#handleFallDamage` →
 * `LivingEntity#damage(fall, …)`, so the same reduction can be expressed at `LivingHurtEvent`, where
 * `amount` is already mutable — no new mixin, and it composes with `void_protection` (which *cancels*
 * the fall entirely) instead of racing it.
 *
 * ## Why `.coerceAtLeast(0f)`
 * At IV the factor is `1 − 0.80 = 0.20`; the clamp only exists so a future level change cannot produce
 * a negative amount, which `EntityDamageMixin` would happily forward as *healing*.
 */
@ModEnchantment(
    id = "feather_fall",
    category = EnchantCategory.COMMON,
    type = EnchantType.BOOTS,
    maxLevel = 4,
    anvilCost = 2,
    minCostBase = 18,
    supportedItems = EnchantSupportedItemTags.FOOT_ARMOR,
    slots = [EnchantSlot.FEET],
)
object EnchantFeatherFallHandler : Listenable {

    /** Fraction of incoming fall damage removed per level. IV = −80 %. */
    const val REDUCTION_PER_LEVEL = 0.20f

    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        if (e.source.isAuxiliaryDamage()) return
        val player = e.entityLiving as? PlayerEntity ?: return
        if (!e.source.isIn(DamageTypeTags.IS_FALL)) return

        val boots = player.getEquippedStack(EquipmentSlot.FEET)
        val level = getItemSpecificEnchantLevel(boots, GeneratedEnchantments.FEATHER_FALL)
        if (level <= 0) return

        val before = e.amount
        e.amount *= (1f - REDUCTION_PER_LEVEL * level).coerceAtLeast(0f)

        FSTLog.log("FeatherFall") {
            "player=${player.name.string}, lvl=$level, damage=$before->${e.amount}, " +
                "outcome=fall-reduced"
        }
    }
}
