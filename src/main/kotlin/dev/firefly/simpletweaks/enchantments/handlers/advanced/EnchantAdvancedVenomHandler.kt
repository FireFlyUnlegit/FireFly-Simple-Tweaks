package dev.firefly.simpletweaks.enchantments.handlers.advanced

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.attacker
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
import dev.firefly.simpletweaks.util.combatStack
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.player.PlayerEntity

/** The advanced form of `venom`: poison on hit, and extra damage against an already poisoned target. */
@ModEnchantment(
    id = "advanced_venom",
    category = EnchantCategory.ADVANCED_RARE,
    type = EnchantType.SWORD,
    maxLevel = 3,
    weight = EnchantItemWeights.ADVANCED_RARE,
    anvilCost = 10,
    minCostBase = 35,
    minCostPerLevel = 15,
    supportedItems = EnchantSupportedItemTags.SWORD,
    slots = [EnchantSlot.MAINHAND],
    order = 75,
)
object EnchantAdvancedVenomHandler : Listenable {

    private const val BASE_LEVEL = 3
    private const val TICKS_PER_LEVEL = 60
    private const val BONUS_PER_LEVEL = 0.05f

    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        val player = e.attacker as? PlayerEntity ?: return
        val victim = e.entityLiving
        if (victim === player) return

        val level = getItemSpecificEnchantLevel(player.combatStack(e.source), GeneratedEnchantments.ADVANCED_VENOM)
        if (level <= 0) return

        val poisoned = victim.hasStatusEffect(StatusEffects.POISON)
        if (poisoned) e.amount *= 1f + BONUS_PER_LEVEL * level
        victim.addStatusEffect(StatusEffectInstance(StatusEffects.POISON, TICKS_PER_LEVEL * (BASE_LEVEL + level), 0))
        FSTLog.log("AdvancedVenom") {
            "player=${player.name.string}, level=$level, poisoned=$poisoned, damage=${e.amount}, outcome=poisoned"
        }
    }
}
