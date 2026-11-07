package dev.firefly.simpletweaks.compat

import dev.firefly.simpletweaks.SimpleTweaks
import org.slf4j.LoggerFactory

/**
 * Opt-in debug logging for enchantment handlers.
 *
 * Emits exactly the agreed shape:
 * ```
 * [ST-<EnchantName>] <key=value, key=value, ...>
 * ```
 * via `LOGGER.info("[ST-{}] {}", enchant, details)`.
 *
 * ## Off by default
 * A normal play session must not write a single `[ST-*]` line. This used to be on by default, which
 * meant every handler that acted also logged — fine for a verification run, pure noise for actually
 * playing. Debug output is now a **startup switch**:
 *
 * | how | value |
 * |---|---|
 * | JVM argument | `-Dsimpletweaks.debug=true` |
 * | environment variable | `SIMPLETWEAKS_DEBUG=true` (or `1`) |
 *
 * The environment variable exists so `gradlew runClient` can be turned verbose without editing
 * `build.gradle`; the JVM argument is what an installed client uses (add it to the version's
 * `arguments.jvm` in the launcher profile).
 *
 * [enabled] is read **once, at class-init**, deliberately: this is a startup switch, not a runtime
 * toggle, and caching it keeps the hot-path check a plain field read.
 *
 * ## Why a helper instead of inlining the logger call
 *  - one place to switch the noise off
 *  - guarantees the `[ST-...]` prefix stays greppable, which is what the acceptance checklist relies
 *    on (`Select-String '\[ST-'` in `run/logs/latest.log`) — see §M of `docs/acceptance-checklist.md`
 *
 * ## Volume
 * Handlers must only log where the enchantment **acts**, never on an early-return path and never
 * once per tick unconditionally — several handlers sit on `PlayerTickEvent` / `LivingUpdateEvent`,
 * which run per player per tick, and unconditional logging there would flood the log and slow the
 * server. For tick-driven state machines, log on **state transitions** only. Prefer the lambda
 * overload below so the detail string is not even built when logging is off.
 */
object STLog {

    private val LOGGER = LoggerFactory.getLogger("${SimpleTweaks.MOD_ID}/debug")

    /**
     * True only when a startup switch asked for debug output.
     *
     * Read once at class-init — see the class KDoc. A Java mixin reaches this as
     * `STLog.INSTANCE.getEnabled()`.
     */
    val enabled: Boolean =
        isOn(System.getProperty("simpletweaks.debug")) || isOn(System.getenv("SIMPLETWEAKS_DEBUG"))

    private fun isOn(value: String?): Boolean =
        value != null && (value.equals("true", ignoreCase = true) || value.trim() == "1")

    fun log(enchant: String, details: String) {
        if (!enabled) return
        LOGGER.info("[ST-{}] {}", enchant, details)
    }

    /**
     * Convenience for the common "compute the detail string only when logging" case — the lambda is
     * only evaluated when logging is on, so string building costs nothing in a normal run.
     */
    inline fun log(enchant: String, details: () -> String) {
        if (!enabled) return
        log(enchant, details())
    }
}
