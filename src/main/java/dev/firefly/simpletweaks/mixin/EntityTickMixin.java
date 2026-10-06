package dev.firefly.simpletweaks.mixin;

import dev.firefly.simpletweaks.compat.ForgeEventBus;
import dev.firefly.simpletweaks.compat.event.LivingEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Re-creates Forge's {@code LivingEvent.LivingUpdateEvent} seam (3 usages).
 *
 * <p><b>Why the target is {@code Entity}, not {@code LivingEntity}:</b> the cached yarn mappings
 * declare {@code tick()} only on {@code net/minecraft/entity/Entity} ({@code method_5773}, official
 * {@code l}); {@code LivingEntity} does not override it. Injecting into {@code LivingEntity#tick}
 * would therefore not resolve at all. Non-living entities are excluded with an {@code instanceof}
 * filter, which is cheap (one type check per entity tick).
 *
 * <p>Fired on both logical sides, matching Forge; the handlers guard themselves
 * (e.g. {@code EnchantCombatMasterHandler} checks {@code entity.world.isRemote}).
 */
@Mixin(Entity.class)
public abstract class EntityTickMixin {

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void simpletweaks$fireLivingUpdate(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof LivingEntity living)) {
            return;
        }
        ForgeEventBus.post(new LivingEvent.LivingUpdateEvent(living));
    }
}
