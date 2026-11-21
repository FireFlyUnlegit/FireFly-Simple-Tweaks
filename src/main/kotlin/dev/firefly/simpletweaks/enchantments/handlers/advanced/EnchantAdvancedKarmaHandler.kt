package dev.firefly.simpletweaks.enchantments.handlers.advanced

import dev.firefly.simpletweaks.compat.FSTLog
import java.util.UUID
import net.minecraft.entity.LivingEntity
import dev.firefly.simpletweaks.compat.attacker
import dev.firefly.simpletweaks.compat.event.LivingDamageEvent
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
import dev.firefly.simpletweaks.util.FireBurn
import dev.firefly.simpletweaks.util.absorptionHeadroomId
import dev.firefly.simpletweaks.util.combatStack
import dev.firefly.simpletweaks.util.damagesource.ModDamageTypes
import dev.firefly.simpletweaks.util.damagesource.damageElemental
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.grantAbsorption
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.player.PlayerEntity
/** The advanced form of this enchantment: its top tier plus an effect of its own. */
@ModEnchantment(
    id = "advanced_karma",
    category = EnchantCategory.ADVANCED_MYSTERY,
    type = EnchantType.ARMOR,
    maxLevel = 4,
    weight = EnchantItemWeights.ADVANCED_MYSTERY,
    damagePerLevel = 0.0,
    anvilCost = 12,
    minCostBase = 35,
    minCostPerLevel = 15,
    supportedItems = EnchantSupportedItemTags.ARMOR,
    slots = [EnchantSlot.ARMOR],
    order = 101,
)
object EnchantAdvancedKarmaHandler : Listenable {


    /** Damage the armour refused, waiting to be spent. */
    private val pool = java.util.Collections.synchronizedMap(HashMap<UUID, Float>())

    @SubscribeEvent(priority = 45)
    fun onKarmaPayback(e: LivingDamageEvent) {
        if (e.invalid) return
        val player = e.attacker as? PlayerEntity ?: return
        if (e.entityLiving === player) return
        val owed = pool.remove(player.uuid) ?: return
        if (owed <= 0f) return
        e.entityLiving.damageElemental(player, ModDamageTypes.TRUE_DAMAGE, owed * 2f)
        FSTLog.log("AdvancedKarma") { "player=${player.name.string}, owed=$owed, outcome=paid-back" }
    }

    private const val BASE_LEVEL = 4

    @SubscribeEvent
    fun onLivingDamage(e: LivingDamageEvent) {
        if (e.invalid) return
        val player = e.entityLiving as? PlayerEntity ?: return
        var level = 0
        for (slot in EquipmentSlot.entries) {
            if (slot.type != EquipmentSlot.Type.HUMANOID_ARMOR) continue
            level += getItemSpecificEnchantLevel(player.getEquippedStack(slot), GeneratedEnchantments.ADVANCED_KARMA)
        }
        if (level <= 0) return
        val before = e.amount
        e.amount *= (1f - 0.04f * (BASE_LEVEL + level)).coerceAtLeast(0f)
        // What the armour saved is not lost: it is held in karma and paid back on the next swing.
        val saved = before - e.amount
        if (saved > 0f) pool[player.uuid] = (pool[player.uuid] ?: 0f) + saved
        FSTLog.log("AdvancedKarmaHandler") { "player=${player.name.string}, level=$level, damage=$before->${e.amount}, outcome=applied" }
    }
}