package dev.firefly.simpletweaks.compat.event

import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack

/**
 * 1.21 replacement for Forge's `ArrowLooseEvent`.
 *
 * Fired from `mixin/BowItemArrowLooseMixin` at the head of `BowItem#onStoppedUsing`, which is the
 * same point Forge used (the bow has been released, the shot has not been fired yet).
 *
 * <p>Handlers can cancel the event to replace vanilla's shot entirely — that is what
 * [dev.firefly.simpletweaks.enchantments.handlers.legendary.EnchantMultishotHandler] does.
 *
 * <p>1.12.2 field names are kept so handler bodies port unchanged:
 * [entityPlayer], [bow], [charge]. [charge] is **ticks drawn**, i.e.
 * `getMaxUseTime(stack, user) - remainingUseTicks`, matching Forge's value (Forge computed exactly
 * the same difference), so 1.12.2's `charge < 5` and `charge / 20.0f` expressions remain meaningful.
 */
class ArrowLooseEvent(
    /** The player releasing the bow. */
    val entityPlayer: PlayerEntity,
    /** The bow being released. */
    val bow: ItemStack,
    /** Ticks the bow was drawn for (0..~20 for a fully drawn vanilla bow). */
    val charge: Int,
) : CancelableEvent()
