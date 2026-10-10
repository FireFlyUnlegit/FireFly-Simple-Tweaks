package dev.firefly.simpletweaks.enchantments.handlers.legendary

import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.event.TickEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.compat.syncEffectToClient
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.*
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.mixin.accessors.StatusEffectInstanceAccessor
import dev.firefly.simpletweaks.util.getArmorEnchantLevel
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.server.network.ServerPlayerEntity

@ModEnchantment(
    id = "curse_resistance",
    category = EnchantCategory.LEGENDARY,
    type = EnchantType.ARMOR,
    maxLevel = 4,
    weight = EnchantItemWeights.LEGENDARY,
    anvilCost = 4,
    minCostBase = 25,
    minCostPerLevel = 12,
    supportedItems = EnchantSupportedItemTags.ARMOR,
    slots = [EnchantSlot.ARMOR],
)
object EnchantCurseResistanceHandler : Listenable {

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.invalid) return
        val p = e.player
        if (p.world.isClient) return

        val lvl = p.getArmorEnchantLevel(GeneratedEnchantments.CURSE_RESISTANCE)
        if (lvl <= 0) return

        if (p.world.random.nextFloat() >= 0.05f * lvl) return

        // Writing `duration` in place does not notify the client (see EffectSync's KDoc). The removal
        // branch is fine -- `removeStatusEffect` sends its own packet -- but a decrement is not, so
        // those instances are collected and re-sent afterwards.
        val shortened = mutableListOf<StatusEffectInstance>()
        for (effect in p.statusEffects.toList()) {
            if (!effect.effectType.value().isBeneficial) {
                val newDuration = effect.duration - 1
                if (newDuration <= 0) {
                    p.removeStatusEffect(effect.effectType)
                } else {
                    (effect as StatusEffectInstanceAccessor)
                        .`simpletweaks$setDuration`(newDuration)
                    shortened += effect
                }
            }
        }
        if (shortened.isNotEmpty()) {
            (p as? ServerPlayerEntity)?.let { player ->
                for (effect in shortened) player.syncEffectToClient(effect)
            }
        }
    }
}