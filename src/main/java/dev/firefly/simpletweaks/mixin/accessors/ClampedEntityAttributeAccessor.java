package dev.firefly.simpletweaks.mixin.accessors;

import net.minecraft.entity.attribute.ClampedEntityAttribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads the raw fields of {@link ClampedEntityAttribute}.
 *
 * <p>The getters are patched to answer from `AttributeCaps`, so anything that needs the *shipped* bound
 * (the GUI's "put it back to vanilla" number, and the fallback inside `AttributeCaps` itself) has to go
 * through the fields — calling `getMinValue()` from there would recurse until the stack dies.
 */
@Mixin(ClampedEntityAttribute.class)
public interface ClampedEntityAttributeAccessor {

    @Accessor("minValue")
    double simpletweaksReadMin();

    @Accessor("maxValue")
    double simpletweaksReadMax();
}
