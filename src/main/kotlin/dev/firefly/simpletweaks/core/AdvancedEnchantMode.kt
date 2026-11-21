package dev.firefly.simpletweaks.core

import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.network.ServerPlayerEntity
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-player toggle for the enchantment table's advanced mode.
 *
 * <p>State lives on the server only: the cost of a roll is decided inside `onButtonClick` and
 * `method_17410`, both of which run on the server thread, and a client that lied about the flag would
 * only change its own button label.
 */
object AdvancedEnchantMode {

    private val enabled: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    /** Per-table flag; keyed by identity so closing the screen is enough to forget it. */
    private val handlers: MutableMap<Any, Boolean> =
        java.util.Collections.synchronizedMap(java.util.WeakHashMap<Any, Boolean>())

    /** True while the offer list for this handler is being generated. */
    private val rolling: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }

    /** Mirror for the button label; not used for any cost decision. */
    @JvmStatic
    var clientOn: Boolean = false

    @JvmStatic
    fun isOn(player: PlayerEntity): Boolean = enabled.contains(player.uuid)

    @JvmStatic
    fun setOn(player: ServerPlayerEntity, value: Boolean) {
        if (value) enabled.add(player.uuid) else enabled.remove(player.uuid)
    }

    @JvmStatic
    fun isAdvancedHandler(handler: Any?): Boolean = handler != null && handlers[handler] == true

    @JvmStatic
    fun setAdvancedHandler(handler: Any?, value: Boolean) {
        if (handler == null) return
        if (value) handlers[handler] = true else handlers.remove(handler)
    }

    /** True while the offer list of the current table is being generated. */
    @JvmStatic
    fun inAdvancedRoll(): Boolean = rolling.get()

    @JvmStatic
    fun beginRoll(value: Boolean) {
        rolling.set(value)
    }

    @JvmStatic
    fun endRoll() {
        rolling.remove()
    }

    @JvmStatic
    fun clear(player: PlayerEntity) {
        enabled.remove(player.uuid)
    }

    /** XP levels a roll costs. `power` is the number shown next to the button. */
    @JvmStatic
    fun xpCost(power: Int): Int = power.coerceAtLeast(1)

    /** Lapis a roll costs: a third of [xpCost], at least one, at most a stack. */
    @JvmStatic
    fun lapisCost(power: Int): Int = (xpCost(power) / 3).coerceIn(1, 64)
}
