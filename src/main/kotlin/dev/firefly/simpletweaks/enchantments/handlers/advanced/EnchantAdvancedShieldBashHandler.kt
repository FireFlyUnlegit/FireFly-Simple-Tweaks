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
import dev.firefly.simpletweaks.util.combatStack
import dev.firefly.simpletweaks.util.damagesource.ModDamageTypes
import dev.firefly.simpletweaks.util.damagesource.damageElemental
import dev.firefly.simpletweaks.util.damagesource.isAuxiliaryDamage
import dev.firefly.simpletweaks.util.UpgradeEnchantments
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity

/** The advanced form of this enchantment: its top tier plus an effect of its own. */
@ModEnchantment(
    id = "advanced_shield_bash",
    category = EnchantCategory.ADVANCED_UNCOMMON,
    type = EnchantType.HELD,
    maxLevel = 3,
    ladderBase = 3,
    weight = EnchantItemWeights.ADVANCED_UNCOMMON,
    anvilCost = 16,
    minCostBase = 40,
    minCostPerLevel = 20,
    supportedItems = EnchantSupportedItemTags.SHIELD,
    slots = [EnchantSlot.OFFHAND],
    order = 92,
)
object EnchantAdvancedShieldBashHandler : Listenable {

    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        if (e.source.isAuxiliaryDamage()) return
        val player = e.entityLiving as? PlayerEntity ?: return
        val attacker = e.source.attacker as? LivingEntity ?: return
        if (attacker === player) return
        if (!player.isBlocking) return
        val level = getItemSpecificEnchantLevel(player.combatStack(e.source), GeneratedEnchantments.ADVANCED_SHIELD_BASH)
        val offhandLevel = getItemSpecificEnchantLevel(player.offHandStack, GeneratedEnchantments.ADVANCED_SHIELD_BASH)
        val effective = maxOf(level, offhandLevel)
        if (effective <= 0) return

        // A raised shield answers the swing instead of only eating it.
        val bash = e.amount * 0.15f * effective
        if (bash <= 0f) return
        val landed = attacker.damageElemental(player, ModDamageTypes.TRUE_DAMAGE, bash)
        FSTLog.log("AdvancedShieldBashHandler") {
            "player=${player.name.string}, level=$effective, bash=$bash, landed=$landed, outcome=bashed"
        }
    }
}
