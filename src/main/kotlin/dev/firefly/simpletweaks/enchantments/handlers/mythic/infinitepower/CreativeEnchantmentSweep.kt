package dev.firefly.simpletweaks.enchantments.handlers.mythic.infinitepower

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.compat.WorldSide
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.event.TickEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.util.LootEnchantmentGuard

/**
 * Last line of defence for a creative-only enchantment that reached a survival player by a path the loot
 * functions do not cover: a third-party mod writing `ENCHANTMENTS` straight onto an item, a `/data` edit, or a
 * save that predates the loot guard. Loot from `enchant_with_levels` / `enchant_randomly` is already stripped at
 * the function exit, so this scan is a backstop and runs once a second rather than every tick.
 *
 * <p>It removes the enchantment, not the item: deleting a player's gear because of one leaked line would be a
 * worse failure than the leak itself. Creative and spectator players keep theirs, since that is where the
 * enchantment is meant to exist.
 */
object CreativeEnchantmentSweep : Listenable {

    private const val INTERVAL = 20

    @SubscribeEvent
    fun onPlayerTick(e: TickEvent.PlayerTickEvent) {
        if (e.invalid) return
        val player = e.player
        if (WorldSide.isClient(player.world)) return
        if (player.isCreative || player.isSpectator) return
        if (player.age % INTERVAL != 0) return

        val inventory = player.inventory
        var stripped = 0
        for (slot in 0 until inventory.size()) {
            if (LootEnchantmentGuard.stripInPlace(inventory.getStack(slot))) stripped++
        }
        if (stripped > 0) {
            FSTLog.log("Loot") { "outcome=swept, player=${player.name.string}, slots=$stripped" }
        }
    }
}
