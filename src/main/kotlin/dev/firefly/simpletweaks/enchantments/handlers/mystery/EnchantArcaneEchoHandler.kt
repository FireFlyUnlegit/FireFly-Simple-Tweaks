package dev.firefly.simpletweaks.enchantments.handlers.mystery

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.WorldSide
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.compat.event.LivingDamageEvent
import dev.firefly.simpletweaks.compat.event.PlayerEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.event.TickEvent
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.annotations.EnchantCategory
import dev.firefly.simpletweaks.enchantments.annotations.EnchantItemWeights
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSlot
import dev.firefly.simpletweaks.enchantments.annotations.EnchantSupportedItemTags
import dev.firefly.simpletweaks.enchantments.annotations.EnchantType
import dev.firefly.simpletweaks.enchantments.annotations.ModEnchantment
import dev.firefly.simpletweaks.util.ArcaneEcho
import net.minecraft.entity.player.PlayerEntity

/**
 * `arcane_echo`: every enchantment level on the wearer feeds one shared bonus package (attributes,
 * flat damage reduction, elemental / reaction amplification) — see [ArcaneEcho] for the numbers.
 */
@ModEnchantment(
    id = "arcane_echo",
    category = EnchantCategory.MYSTERY,
    type = EnchantType.EQUIPMENT,
    maxLevel = 3,
    weight = EnchantItemWeights.MYSTERY,
    anvilCost = 18,
    minCostBase = 50,
    minCostPerLevel = 20,
    supportedItems = EnchantSupportedItemTags.EQUIPMENT,
    slots = [EnchantSlot.MAINHAND, EnchantSlot.ARMOR],
    order = 58,
)
object EnchantArcaneEchoHandler : Listenable {

    /** Enchantment sums change only on gear edits; a per-tick scan of six stacks is unnecessary. */
    private const val REFRESH_INTERVAL_TICKS = 20

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.phase != TickEvent.Phase.END) return
        val player = e.player
        if (WorldSide.isClient(player.world)) return
        if (player.age % REFRESH_INTERVAL_TICKS != 0) return
        ArcaneEcho.refresh(player)
    }

    @SubscribeEvent(priority = 40)
    fun onLivingDamage(e: LivingDamageEvent) {
        if (e.invalid) return
        val victim = e.entityLiving as? PlayerEntity ?: return
        if (WorldSide.isClient(victim.world)) return
        val stats = ArcaneEcho.statsOf(victim)
        if (!stats.active || stats.damageReduction <= 0.0) return
        val before = e.amount
        e.amount = (e.amount - stats.damageReduction.toFloat()).coerceAtLeast(0f)
        FSTLog.log("ArcaneEcho") {
            "victim=${victim.name.string}, level=${stats.level}, units=${stats.units}, " +
                "flat=${"%.2f".format(stats.damageReduction)}, amount=$before->${e.amount}, outcome=absorbed"
        }
    }

    @SubscribeEvent
    fun onClone(e: PlayerEvent.Clone) {
        ArcaneEcho.clear(e.original)
        // The new body copies attributes from the old one; recompute right away so max health is back
        // before anything can clamp against it.
        ArcaneEcho.refresh(e.player)
    }

    /**
     * Cache only: the modifiers are persistent and must still be in the entity when the server writes the
     * player file, otherwise the next login clamps max health back to 20 and the saved health is lost.
     */
    @SubscribeEvent
    fun onLogout(e: PlayerEvent.PlayerLoggedOutEvent) {
        ArcaneEcho.clearCache(e.player)
    }
}
