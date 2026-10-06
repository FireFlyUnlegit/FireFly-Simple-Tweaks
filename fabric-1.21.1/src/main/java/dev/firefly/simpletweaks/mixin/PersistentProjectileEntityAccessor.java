package dev.firefly.simpletweaks.mixin;

import net.minecraft.entity.projectile.PersistentProjectileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads `PersistentProjectileEntity#inGround`, which 1.21 keeps `protected` with no getter.
 *
 * <h2>1.12.2 original</h2>
 * `EntityArrowAccessor` did the same job for `EntityArrow.inGround`. The tracking handler uses it to
 * skip arrows that have already landed — without it, a stuck arrow would keep steering toward nearby
 * mobs forever, because its velocity vector stays non-zero.
 *
 * <p>An accessor mixin rather than an access widener: this is a single read, and the accessor keeps
 * the widening local to this mod instead of changing the class for every other mod in the instance.
 */
@Mixin(PersistentProjectileEntity.class)
public interface PersistentProjectileEntityAccessor {

    @Accessor("inGround")
    boolean simpletweaks$getInGround();
}
