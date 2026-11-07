package dev.firefly.simpletweaks.enchantments.handlers.epic

import dev.firefly.simpletweaks.compat.WorldSide
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.event.TickEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.*
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.absorptionHeadroomId
import dev.firefly.simpletweaks.util.ensureAbsorptionHeadroom
import dev.firefly.simpletweaks.util.removeAbsorptionHeadroom
import dev.firefly.simpletweaks.util.chestplate
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.player.PlayerEntity

/**
 * 护盾强化 — raises `generic.max_absorption`, the ceiling every absorption effect is clamped by.
 *
 * ## Why this is the missing piece
 * `AGENTS.md` §4.9 records that `PlayerEntity#setAbsorptionAmount` clamps to `getMaxAbsorption()`, which
 * reads `generic.max_absorption` — **zero by default**, only raised by the `absorption` status effect. Every
 * absorption-based enchantment in this mod (`生命虹吸`, `回响之盾`, `死亡保护`) therefore had a hard ceiling of
 * "whatever the potion gave you". This enchantment raises that ceiling itself.
 *
 * ## Mechanics
 * A **temporary** attribute modifier, one per source id (`AbsorptionUtil` owns the id scheme), refreshed on a
 * slow tick and removed the moment the chestplate loses the enchantment. Temporary is required: the value
 * belongs to what you are wearing, so it must not survive taking the armour off (see §4.2).
 */
@ModEnchantment(
    id = "aegis",
    category = EnchantCategory.EPIC,
    type = EnchantType.CHESTPLATE,
    maxLevel = 3,
    anvilCost = 8,
    minCostBase = 24,
    supportedItems = EnchantSupportedItemTags.CHEST_ARMOR,
    slots = [EnchantSlot.CHEST],
)
object EnchantAegisHandler : Listenable {

    private const val PER_LEVEL = 2.0f
    private const val CAP = 8.0f

    /** Refreshed every 20 ticks: the value only changes when the armour does. */
    private const val INTERVAL = 20

    private val modifierId = absorptionHeadroomId("aegis")

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        val player = e.player
        if (WorldSide.isClient(player.world)) return
        if (player.age % INTERVAL != 0) return

        val level = getItemSpecificEnchantLevel(player.chestplate, GeneratedEnchantments.AEGIS)
        if (level <= 0) {
            removeAbsorptionHeadroom(player, modifierId)
            return
        }
        ensureAbsorptionHeadroom(player, modifierId, (PER_LEVEL * level).coerceAtMost(CAP))
    }
}
