package dev.firefly.simpletweaks.client

import java.util.*

object ClientManaPoolCache {
    private val cache = mutableMapOf<UUID, Float>()

    fun update(uuid: UUID, value: Float) {
        cache[uuid] = value
    }

    fun get(uuid: UUID): Float = cache[uuid] ?: 0f

    fun clear() {
        cache.clear()
    }

}