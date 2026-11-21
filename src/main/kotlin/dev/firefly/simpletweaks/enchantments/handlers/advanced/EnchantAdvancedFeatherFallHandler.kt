package dev.firefly.simpletweaks.enchantments.handlers.advanced

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantItemWeights
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSupportedItemTags
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.registry.tag.DamageTypeTags

/**
 * The advanced form of `feather_fall`: a IV-grade reduction that reaches zero, plus a short burst of
 * speed when the landing would have hurt.
 */
@ModEnchantment(
    id = "advanced_feather_fall",
    category = EnchantCategory.ADVANCED_COMMON,
    type = EnchantType.BOOTS,
    maxLevel = 3,
    weight = EnchantItemWeights.ADVANCED_COMMON,
    anvilCost = 5,
    minCostBase = 30,
    minCostPerLevel = 15,
    supportedItems = EnchantSupportedItemTags.FOOT_ARMOR,
    slots = [EnchantSlot.FEET],
    order = 71,
)
object EnchantAdvancedFeatherFallHandler : Listenable {

    /** The base form is treated as level IV, so the reduction starts at 80 %. */
    private const val BASE_LEVEL = 4

    private const val REDUCTION_PER_LEVEL = 0.20f

    /** Ticks of speed per level, granted on every landing that would have hurt. */
    private const val SPEED_TICKS_PER_LEVEL = 40

    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        val player = e.entityLiving as? PlayerEntity ?: return
        if (!e.source.isIn(DamageTypeTags.IS_FALL)) return

        val level = getItemSpecificEnchantLevel(
            player.getEquippedStack(EquipmentSlot.FEET),
            GeneratedEnchantments.ADVANCED_FEATHER_FALL,
        )
        if (level <= 0) return

        val before = e.amount
        e.amount *= (1f - REDUCTION_PER_LEVEL * (BASE_LEVEL + level)).coerceAtLeast(0f)
        if (before > 0f) {
            player.addStatusEffect(StatusEffectInstance(StatusEffects.SPEED, SPEED_TICKS_PER_LEVEL * level, 1))
        }

        FSTLog.log("AdvancedFeatherFall") {
            "player=${player.name.string}, level=$level, damage=$before->${e.amount}, " +
                "outcome=fall-absorbed"
        }
    }
}
