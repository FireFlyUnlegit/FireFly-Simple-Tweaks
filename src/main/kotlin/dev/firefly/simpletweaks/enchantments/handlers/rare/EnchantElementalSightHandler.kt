package dev.firefly.simpletweaks.enchantments.handlers.rare

import dev.firefly.simpletweaks.compat.WorldSide
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.event.TickEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.*
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.boots
import dev.firefly.simpletweaks.util.elements.ElementAuras
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.hit.EntityHitResult

/**
 * 元素视界 — shows the target's **aura gauge** in the action bar, so the elemental system is observable.
 *
 * ## Why this exists at all
 * `build=cleanup23` gave every hit an aura amount, a decay rate and a 2.5 s attachment ICD. None of it was
 * visible in game, which makes the whole system unfalsifiable for a player: "my reaction did not happen"
 * and "my reaction was throttled by an ICD I cannot see" look identical. This enchantment is the
 * observation tool, not a power increase.
 *
 * ## Seams
 * - `PlayerTickEvent` every 10 ticks (a per-tick action-bar write would spam the packet stream; 2 Hz is
 *   enough to read a number that decays over 9.5 s).
 * - `PlayerEntity#raycast(16, …)` finds what the player is looking at, server side, so no client code and
 *   no new packet is needed — the action bar is vanilla.
 */
@ModEnchantment(
    id = "elemental_sight",
    category = EnchantCategory.RARE,
    type = EnchantType.BOOTS,
    maxLevel = 1,
    anvilCost = 4,
    minCostBase = 20,
    supportedItems = EnchantSupportedItemTags.FOOT_ARMOR,
    slots = [EnchantSlot.FEET],
)
object EnchantElementalSightHandler : Listenable {

    private const val SCAN_INTERVAL = 10
    private const val RANGE = 16.0

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        val player = e.player
        if (WorldSide.isClient(player.world)) return
        if (player.age % SCAN_INTERVAL != 0) return

        val level = getItemSpecificEnchantLevel(player.boots, GeneratedEnchantments.ELEMENTAL_SIGHT)
        if (level <= 0) return

        val hit = player.raycast(RANGE, 0.0f, false) as? EntityHitResult ?: return
        val target = hit.entity as? LivingEntity ?: return

        player.sendMessage(sightText(target), true)
    }

    private fun sightText(target: LivingEntity): Text {
        val aura = ElementAuras.peek(target)
            ?: return Text.translatable("message.simple_tweaks.elemental_sight.none", target.name)
        return Text.translatable(
            "message.simple_tweaks.elemental_sight.aura",
            target.name,
            Text.translatable("element.simple_tweaks.${aura.first.id}"),
            "%.2f".format(aura.second),
        )
    }
}
