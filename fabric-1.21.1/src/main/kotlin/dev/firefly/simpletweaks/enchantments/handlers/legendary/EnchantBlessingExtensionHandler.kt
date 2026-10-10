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
    id = "blessing_extension",
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
object EnchantBlessingExtensionHandler : Listenable {

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.invalid) return
        val p = e.player
        if (p.world.isClient) return

        val lvl = p.getArmorEnchantLevel(GeneratedEnchantments.BLESSING_EXTENSION)
        if (lvl <= 0) return

        if (p.world.random.nextFloat() >= 0.05f * lvl) return

        // Writing `duration` in place does not notify the client (see EffectSync's KDoc), so the
        // instances we touched are collected and re-sent afterwards.
        val extended = mutableListOf<StatusEffectInstance>()
        for (effect in p.statusEffects.toList()) {
            if (effect.effectType.value().isBeneficial) {
                (effect as StatusEffectInstanceAccessor)
                    .`simpletweaks$setDuration`(effect.duration + 1)
                extended += effect
            }
        }
        if (extended.isNotEmpty()) {
            (p as? ServerPlayerEntity)?.let { player ->
                for (effect in extended) player.syncEffectToClient(effect)
            }
        }
    }
}