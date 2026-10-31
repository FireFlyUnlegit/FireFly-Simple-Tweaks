package dev.firefly.simpletweaks.compat.event

/**
 * Drop-in replacement for `net.minecraftforge.fml.common.eventhandler.SubscribeEvent`
 * (that package no longer exists; `MinecraftForge.EVENT_BUS` is gone entirely in 1.21 Fabric).
 *
 * Porting rule for handler files: keep the annotation and the method body untouched, and only
 * change the import to this class. [ForgeEventBus.register] discovers these reflectively, exactly
 * the way Forge did.
 *
 * ## [priority] is an `Int`, not an enum
 * Ascending value = running order; smaller runs earlier. Leave it out to get [EventPriority.NORMAL]
 * (`0`), which is what a new handler should do. The five named values are `HIGHEST -100 /
 * HIGH -50 / NORMAL 0 / LOW 50 / LOWEST 100`; anything between or beyond them is legal inside
 * [EventPriority.MIN_PRIORITY]`..`[EventPriority.MAX_PRIORITY]`, and a value outside that range is
 * clamped at registration with a `warn` (see [EventPriority]).
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class SubscribeEvent(
    val priority: Int = EventPriority.NORMAL,
    val receiveCanceled: Boolean = false
)
