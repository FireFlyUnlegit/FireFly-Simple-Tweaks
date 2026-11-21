package dev.firefly.simpletweaks.enchantments.handlers.mystery

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.WorldSide
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.compat.event.LivingDamageEvent
import dev.firefly.simpletweaks.compat.event.LivingHealEvent
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
import dev.firefly.simpletweaks.util.ArrowDamageSeam
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

    override fun init() {
        // Bow damage has exactly one seam (see ArrowDamageSeam): register, do not patch the arrow.
        ArrowDamageSeam.register { _, arrow ->
            val shooter = arrow.owner as? PlayerEntity
            val stats = if (shooter == null) null else ArcaneEcho.statsOf(shooter)
            if (stats == null || !stats.active) 1.0f else (1.0 + stats.rangedDamage).toFloat()
        }
    }

    @SubscribeEvent
    fun onLivingHeal(e: LivingHealEvent) {
        val player = e.entityLiving as? PlayerEntity ?: return
        if (WorldSide.isClient(player.world)) return
        val stats = ArcaneEcho.statsOf(player)
        if (!stats.active || stats.healing <= 0.0 || e.amount <= 0f) return
        val before = e.amount
        e.amount = (e.amount * (1.0 + stats.healing)).toFloat()
        FSTLog.log("ArcaneEcho") {
            "player=${player.name.string}, level=${stats.level}, healing=${"%.3f".format(stats.healing)}, " +
                "amount=$before->${e.amount}, outcome=healed"
        }
    }

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
        if (WorldSide.isClient(e.entityLiving.world)) return
        amplifyDealt(e)
        mitigateTaken(e)
    }

    /** Outgoing side: final damage scales the hit that is about to land. */
    private fun amplifyDealt(e: LivingDamageEvent) {
        val attacker = e.source.attacker as? PlayerEntity ?: return
        if (attacker === e.entityLiving) return
        val stats = ArcaneEcho.statsOf(attacker)
        if (!stats.active || stats.finalDamage <= 0.0) return
        val before = e.amount
        e.amount = (e.amount * (1.0 + stats.finalDamage)).toFloat()
        FSTLog.log("ArcaneEcho") {
            "attacker=${attacker.name.string}, level=${stats.level}, " +
                "final=${"%.2f".format(stats.finalDamage)}, amount=$before->${e.amount}, outcome=amplified"
        }
    }

    /** Incoming side: saturating percentage reduction first, then the flat term. */
    private fun mitigateTaken(e: LivingDamageEvent) {
        val victim = e.entityLiving as? PlayerEntity ?: return
        val stats = ArcaneEcho.statsOf(victim)
        if (!stats.active) return
        val before = e.amount
        if (stats.finalReduction > 0.0) {
            e.amount = (e.amount * (1.0 - stats.finalReduction)).toFloat()
        }
        if (stats.damageReduction > 0.0) {
            e.amount = (e.amount - stats.damageReduction.toFloat()).coerceAtLeast(0f)
        }
        if (e.amount == before) return
        FSTLog.log("ArcaneEcho") {
            "victim=${victim.name.string}, level=${stats.level}, units=${stats.units}, " +
                "final=${"%.2f".format(stats.finalReduction)}, flat=${"%.2f".format(stats.damageReduction)}, " +
                "amount=$before->${e.amount}, outcome=mitigated"
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
