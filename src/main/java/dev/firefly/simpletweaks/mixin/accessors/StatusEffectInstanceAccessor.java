package dev.firefly.simpletweaks.mixin.accessors;

import net.minecraft.entity.effect.StatusEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(StatusEffectInstance.class)
public interface StatusEffectInstanceAccessor {
    @Accessor("duration")
    void simpletweaks$setDuration(int duration);
}