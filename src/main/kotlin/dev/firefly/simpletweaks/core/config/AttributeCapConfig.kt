package dev.firefly.simpletweaks.core.config

/**
 * Per-attribute min/max overrides, keyed by registry id (`minecraft:generic.max_health`).
 *
 * An absent attribute keeps the vanilla bounds, so an empty file is a no-op. A present entry with only
 * `max` set raises just that side; `min` stays whatever vanilla declared.
 */
object AttributeCapConfig {

    /** One override; `null` on a side means "keep the vanilla bound for it". */
    class Cap(@JvmField var min: Double? = null, @JvmField var max: Double? = null) {
        val isEmpty: Boolean get() = min == null && max == null
    }

    private val overrides = LinkedHashMap<String, Cap>()

    /** Bumped on every mutation; [dev.firefly.simpletweaks.util.AttributeCaps] drops its cache on change. */
    @Volatile
    var revision: Int = 0
        private set

    @JvmStatic
    fun of(id: String): Cap? = overrides[id]

    @JvmStatic
    fun all(): Map<String, Cap> = overrides

    @JvmStatic
    fun put(id: String, cap: Cap) {
        if (cap.isEmpty) overrides.remove(id) else overrides[id] = cap
        revision++
    }

    @JvmStatic
    fun remove(id: String) {
        if (overrides.remove(id) != null) revision++
    }

    @JvmStatic
    fun clear() {
        overrides.clear()
        revision++
    }
}
