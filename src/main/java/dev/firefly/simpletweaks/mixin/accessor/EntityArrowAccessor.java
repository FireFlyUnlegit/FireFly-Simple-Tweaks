package dev.firefly.simpletweaks.mixin.accessor;

import net.minecraft.entity.projectile.EntityArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityArrow.class)
public interface EntityArrowAccessor {

    @Accessor("inGround")
    boolean getInGround();

    @Accessor("inGround")
    void setInGround(boolean value);
}