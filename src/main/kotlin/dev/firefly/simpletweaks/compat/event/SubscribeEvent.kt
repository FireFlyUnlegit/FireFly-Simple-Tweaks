package dev.firefly.simpletweaks.compat.event

/**
 * Drop-in replacement for `net.minecraftforge.fml.common.eventhandler.SubscribeEvent`
 * (that package no longer exists; `MinecraftForge.EVENT_BUS` is gone entirely in 1.21 Fabric).
 *
 * Porting rule for handler files: keep the annotation and the method body untouched, and only
 * change the import to this class. [ForgeEventBus.register] discovers these reflectively, exactly
 * the way Forge did.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class SubscribeEvent(
    val priority: EventPriority = EventPriority.NORMAL,
    val receiveCanceled: Boolean = false
)
