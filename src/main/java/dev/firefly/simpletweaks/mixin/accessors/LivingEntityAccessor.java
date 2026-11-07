package dev.firefly.simpletweaks.mixin.accessors;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read/write access to `LivingEntity#lastDamageTaken`, which is `protected` and has no public getter.
 *
 * <p>Needed by {@code ElementDamageSources#damageElemental}: the elemental re-deal is a **rider on top of**
 * the swing that produced it, and vanilla's i-frame arithmetic must not treat it as an independent hit.
 * See that function for the measured symptom.
 *
 * <p>Error-prone area: the field participates in `LivingEntity#damage`'s post-gate path
 * (`applyDamage(amount - lastDamageTaken)`, verified in the bytecode at offsets 250–274). Writing it
 * without restoring it silently reduces the *next* real hit by this amount.
 */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {

    @Accessor("lastDamageTaken")
    float getModLastDamageTaken();

    @Accessor("lastDamageTaken")
    void setModLastDamageTaken(float value);
}
