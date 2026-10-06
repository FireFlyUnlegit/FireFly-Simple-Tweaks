package dev.firefly.simpletweaks.core

import dev.firefly.simpletweaks.compat.ForgeEventBus

/**
 * 1.21 port of `core/Listenable.kt`.
 *
 * The old file exposed `registerToForge()`, which called `MinecraftForge.EVENT_BUS.register(this)`
 * and then `init()`. The name is now bus-agnostic because there is no Forge bus any more, but the
 * contract is identical. [ForgeEventBus] discovers `@SubscribeEvent` methods reflectively.
 */
interface Listenable {
    fun shouldHandleEvents(): Boolean = true
    fun init() {}
}

fun Listenable.registerEvents() {
    ForgeEventBus.register(this)
    this.init()
}

fun Listenable.unregisterEvents() {
    ForgeEventBus.unregister(this)
}
