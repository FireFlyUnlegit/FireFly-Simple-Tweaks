package dev.firefly.simpletweaks.util.elements

import dev.firefly.simpletweaks.compat.FSTLog
import dev.firefly.simpletweaks.enchantments.generated.GeneratedEnchantments
import dev.firefly.simpletweaks.util.boots
import dev.firefly.simpletweaks.util.chestplate
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel
import dev.firefly.simpletweaks.util.helmet
import dev.firefly.simpletweaks.util.leggings
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import java.util.ArrayDeque
import java.util.WeakHashMap

/**
 * Remembers the last few reactions that hit a player wearing `elemental_supremacy`.
 *
 * ## What it is for
 * The enchantment's active half: every reaction that lands on the wearer is stored, and the wearer's **next**
 * hit replays up to [CAPACITY] of them onto the target. So a player who gets frozen, shocked and set on fire
 * answers with a freeze, a shock and a fire — "return what you were given".
 *
 * ## Why a queue and not a count
 * The *kind* of reaction matters, not how many: replaying five 「感电」 is a very different swing from
 * replaying one of each. A FIFO deque of keys keeps that information for a few bytes, and cap 5 keeps the
 * payoff bounded (five maximum-amplitude reactions would otherwise be a one-shot nuke).
 *
 * ## Lifetime
 * `WeakHashMap` keyed by player — session state, like `ShotWeapon` and the other per-entity maps here. It is
 * deliberately **not** in NBT: this is a combat combo, not a progression stat, and persisting it would let a
 * player park a loaded queue across a relog.
 */
object ReactionMemory {

    /** How many reactions can be stored and replayed at once. */
    const val CAPACITY = 5

    private val stored = WeakHashMap<PlayerEntity, ArrayDeque<String>>()

    /** Records [key] if [entity] is a player wearing the enchantment; otherwise a no-op. */
    fun recordIfWearing(entity: LivingEntity, key: String) {
        val player = entity as? PlayerEntity ?: return
        if (player.world.isClient) return
        if (getItemSpecificEnchantLevel(player.chestplate, GeneratedEnchantments.ELEMENTAL_SUPREMACY) <= 0 &&
            getItemSpecificEnchantLevel(player.leggings, GeneratedEnchantments.ELEMENTAL_SUPREMACY) <= 0 &&
            getItemSpecificEnchantLevel(player.helmet, GeneratedEnchantments.ELEMENTAL_SUPREMACY) <= 0 &&
            getItemSpecificEnchantLevel(player.boots, GeneratedEnchantments.ELEMENTAL_SUPREMACY) <= 0
        ) {
            return
        }

        val queue = stored.getOrPut(player) { ArrayDeque(CAPACITY) }
        if (queue.size >= CAPACITY) queue.removeFirst()
        queue.addLast(key)

        FSTLog.log("Supremacy") {
            "player=${player.name.string}, stored=$key, queue=${queue.joinToString(",")}, outcome=memorised"
        }
    }

    /** Hands over everything stored and clears it. Empty list when nothing is pending. */
    fun drain(player: PlayerEntity): List<String> {
        val queue = stored.remove(player) ?: return emptyList()
        return queue.toList()
    }
}
