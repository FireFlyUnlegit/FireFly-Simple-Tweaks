package dev.firefly.simpletweaks.enchantments.handlers.legendary

import dev.firefly.simpletweaks.compat.event.EventPriority
import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.compat.target
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantItemWeights
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSupportedItemTags
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.getArmorEnchantLevel
import net.minecraft.entity.player.PlayerEntity
import java.util.UUID
import java.util.WeakHashMap

@ModEnchantment(
    id = "adopted_armor",
    category = EnchantCategory.LEGENDARY,
    type = EnchantType.ARMOR,
    maxLevel = 4,
    weight = EnchantItemWeights.LEGENDARY,
    anvilCost = 4,
    minCostBase = 30,
    minCostPerLevel = 15,
    supportedItems = EnchantSupportedItemTags.ARMOR,
    slots = [EnchantSlot.ARMOR],
)
object EnchantAdoptedArmor : Listenable {

    private class State {
        var lastType: String = ""
        var stacks: Int = 0
        var lastHitTick: Long = 0L
    }

    private val states = WeakHashMap<UUID, State>()

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onLivingHurt(e: LivingHurtEvent) {
        if (e.invalid) return
        val player = e.target as? PlayerEntity ?: return

        val lvl = player.getArmorEnchantLevel(GeneratedEnchantments.ADOPTED_ARMOR)
        if (lvl <= 0) return

        val now = player.world.time
        val type = e.source.name
        val state = states.getOrPut(player.uuid) { State() }

        state.stacks = if (type == state.lastType && now - state.lastHitTick <= 100L) {
            (state.stacks + 1).coerceAtMost(6)
        } else {
            1
        }
        state.lastType = type
        state.lastHitTick = now

        val reduction = (0.009f * lvl * state.stacks).coerceAtMost(1f)
        e.amount *= (1f - reduction)
    }
}