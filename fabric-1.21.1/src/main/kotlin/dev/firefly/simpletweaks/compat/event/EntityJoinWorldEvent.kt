package dev.firefly.simpletweaks.compat.event

import net.minecraft.entity.Entity
import net.minecraft.world.World

/**
 * 1.21 replacement for Forge's `EntityJoinWorldEvent` (2 usages).
 *
 * <p><b>Semantic note:</b> Forge fired this on both logical sides. The only Fabric hook is
 * `ServerEntityEvents.ENTITY_LOAD`, so this event is now **server-side only**. Both 1.12.2 users
 * (`EnchantPiercingArrowHandler`, `EnchantTrackingArrowHandler`) track arrows, and arrows that matter
 * for gameplay exist server-side, so this is acceptable — but if a future handler needs the
 * client-side join, that needs a client mixin.
 */
class EntityJoinWorldEvent(
    val entity: Entity,
    val world: World
) : CancelableEvent()
