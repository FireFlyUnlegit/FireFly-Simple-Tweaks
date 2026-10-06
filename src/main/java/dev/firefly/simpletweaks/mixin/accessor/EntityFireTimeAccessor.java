package dev.firefly.simpletweaks.mixin.accessor;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface EntityFireTimeAccessor {
    @Accessor("fire")
    int simpletweaks$getFire();

    @Accessor("fire")
    void simpletweaks$setFire(int value);
}