package dev.firefly.simpletweaks.enchantments.handlers.advanced

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.event.LivingDamageEvent
import dev.firefly.simpletweaks.compat.event.PlayerEvent
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
import dev.firefly.simpletweaks.util.damagesource.isAuxiliaryDamage
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.player.PlayerEntity
import java.util.UUID

/** The advanced form of this enchantment: its top tier plus an effect of its own. */
@ModEnchantment(
    id = "advanced_immortal",
    category = EnchantCategory.ADVANCED_RARE,
    type = EnchantType.ARMOR,
    maxLevel = 2,
    ladderBase = 1,
    weight = EnchantItemWeights.ADVANCED_RARE,
    damagePerLevel = 0.0,
    anvilCost = 12,
    minCostBase = 35,
    minCostPerLevel = 15,
    supportedItems = EnchantSupportedItemTags.ARMOR,
    slots = [EnchantSlot.ARMOR],
    order = 89,
)
object EnchantAdvancedImmortalHandler : Listenable {

    private const val BASE_LEVEL = 1

    /** One save per minute — without this the wearer is simply un-killable. */
    private const val COOLDOWN_TICKS = 1200L

    /** Saves already spent, keyed by player. */
    private val spent = java.util.Collections.synchronizedMap(HashMap<UUID, Long>())

    @SubscribeEvent
    fun onLivingDamage(e: LivingDamageEvent) {
        if (e.invalid) return
        if (e.source.isAuxiliaryDamage()) return
        val player = e.entityLiving as? PlayerEntity ?: return
        val level = wornLevel(player)
        if (level <= 0) return
        if (e.amount < player.health || player.health <= 1f) return

        val now = player.world.time
        val last = spent[player.uuid] ?: Long.MIN_VALUE
        val ready = now - last >= COOLDOWN_TICKS
        val before = e.amount
        if (ready) {
            spent[player.uuid] = now
            e.amount = player.health - 1f
            player.addStatusEffect(StatusEffectInstance(StatusEffects.RESISTANCE, 60, 2))
        }
        FSTLog.log("AdvancedImmortalHandler") {
            "player=${player.name.string}, level=$level, damage=$before->${e.amount}, ready=$ready, outcome=applied"
        }
    }

    @SubscribeEvent
    fun onLogout(e: PlayerEvent.PlayerLoggedOutEvent) {
        spent.remove(e.player.uuid)
    }

    private fun wornLevel(player: PlayerEntity): Int {
        var level = 0
        for (slot in EquipmentSlot.entries) {
            if (slot.type != EquipmentSlot.Type.HUMANOID_ARMOR) continue
            level += getItemSpecificEnchantLevel(
                player.getEquippedStack(slot),
                GeneratedEnchantments.ADVANCED_IMMORTAL,
            )
        }
        return level
    }
}
