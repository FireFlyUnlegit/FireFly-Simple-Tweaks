package dev.firefly.simpletweaks.compat

import dev.firefly.simpletweaks.SimpleTweaks
import org.slf4j.LoggerFactory

/**
 * Uniform debug logging for enchantment handlers.
 *
 * Emits exactly the agreed shape:
 * ```
 * [ST-<EnchantName>] <key=value, key=value, ...>
 * ```
 * via `LOGGER.info("[ST-{}] {}", enchant, details)`.
 *
 * ## Why a helper instead of inlining the logger call
 *  - one place to switch the noise off
 *  - guarantees the `[ST-...]` prefix stays greppable, which is what the server-side acceptance
 *    checklist relies on (`Select-String '\[ST-'` in `run/logs/latest.log`)
 *
 * ## Volume
 * Handlers must only log where the enchantment **acts**, never on an early-return path and never
 * once per tick unconditionally — several handlers sit on `PlayerTickEvent` / `LivingUpdateEvent`,
 * which run per player per tick, and unconditional logging there would flood the log and slow the
 * server. For tick-driven state machines, log on **state transitions** only.
 *
 * Enabled by default so a verification run needs no JVM flags; silence it with
 * `-Dsimpletweaks.debug=false`.
 */
object STLog {

    private val LOGGER = LoggerFactory.getLogger("${SimpleTweaks.MOD_ID}/debug")

    /** `-Dsimpletweaks.debug=false` disables; anything else (including unset) enables. */
    val enabled: Boolean = System.getProperty("simpletweaks.debug", "true") != "false"

    fun log(enchant: String, details: String) {
        if (!enabled) return
        LOGGER.info("[ST-{}] {}", enchant, details)
    }

    /**
     * Convenience for the common "compute the detail string only when logging" case — the lambda is
     * only evaluated when logging is on, so string building costs nothing in a release run.
     */
    inline fun log(enchant: String, details: () -> String) {
        if (!enabled) return
        log(enchant, details())
    }
}
